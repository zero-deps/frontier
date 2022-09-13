package ws

import java.net.{ServerSocket, Socket, InetAddress}
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

@main def runWebSocketServer(): Unit =
  val port = 9011
  val server = new WebSocketServer(port)
  server.start()

class WebSocketServer(port: Int):

  def start(): Unit =
    val serverSocket = new ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"))
    println(s"WebSocket server is listening on port $port")

    try
      while true do
        val clientSocket = serverSocket.accept() // Blocking call
        Thread.startVirtualThread(() => handleClient(clientSocket))
    finally
      serverSocket.close()

  private def handleClient(socket: Socket): Unit =
    try
      val input = socket.getInputStream
      val output = socket.getOutputStream

      // Perform the WebSocket handshake
      val upgraded = performHandshake(input, output)

      if upgraded then
        // println(s"WebSocket connection established with ${socket.getRemoteSocketAddress}")
        // Handle WebSocket communication
        communicate(input, output)
      else
        println(s"WebSocket handshake failed with ${socket.getRemoteSocketAddress}")
    catch
      case e: Exception =>
        println(s"Error handling client ${socket.getRemoteSocketAddress}: ${e.getMessage}")
    finally
      socket.close()

  private def performHandshake(input: InputStream, output: OutputStream): Boolean =
    val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
    val writer = BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8))

    // Read the HTTP request headers
    val requestLines = readHttpRequest(reader)

    // Extract the 'Sec-WebSocket-Key' header
    val webSocketKeyOpt = requestLines
      .find("(?i)^Sec-WebSocket-Key:".r.findFirstIn(_).isDefined)
      .map(_.substring("Sec-WebSocket-Key:".length).trim)

    webSocketKeyOpt match
      case Some(webSocketKey) =>
        // Generate the 'Sec-WebSocket-Accept' key
        val acceptKey = generateAcceptKey(webSocketKey)

        // Write the HTTP response headers to upgrade the connection
        writer.write("HTTP/1.1 101 Switching Protocols\r\n")
        writer.write("Upgrade: websocket\r\n")
        writer.write("Connection: Upgrade\r\n")
        writer.write(s"Sec-WebSocket-Accept: $acceptKey\r\n")
        writer.write("\r\n")
        writer.flush()

        true // Handshake successful

      case None =>
        // Missing 'Sec-WebSocket-Key' header; cannot perform handshake
        writer.write("HTTP/1.1 400 Bad Request\r\n")
        writer.write("Connection: close\r\n")
        writer.write("\r\n")
        writer.flush()

        false // Handshake failed

  private def readHttpRequest(reader: BufferedReader): List[String] =
    var lines = List.empty[String]
    var line = ""

    // Read until an empty line is encountered (end of headers)
    while { line = reader.readLine(); line != null && line.nonEmpty } do
      lines = lines :+ line

    lines

  private def generateAcceptKey(webSocketKey: String): String =
    val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    val concatenated = webSocketKey + GUID

    // Compute SHA-1 hash of the concatenated string
    val sha1Hash = MessageDigest.getInstance("SHA-1").digest(concatenated.getBytes(StandardCharsets.UTF_8))

    // Encode the hash using Base64
    Base64.getEncoder.encodeToString(sha1Hash)

  private def communicate(input: InputStream, output: OutputStream): Unit =
    val isRunning = AtomicBoolean(true)
    try
      while isRunning.get() do
        val frame = readFrame(input)
        frame match
          case Some(frame) =>
            frame.opcode match
              case 0x1 => // Text frame
                val message = String(frame.payloadData, StandardCharsets.UTF_8)
                // println(s"Received text message: $message")
                // Echo the message back to the client
                sendFrame(output, fin = true, opcode = 0x1, payloadData = frame.payloadData)
              case 0x2 => // Binary frame
                // println(s"Received binary message of length ${frame.payloadData.length}")
                // Handle binary data as needed
                sendFrame(output, fin = true, opcode = 0x2, payloadData = frame.payloadData)
              case 0x8 => // Connection Close frame
                // println("Received close frame")
                isRunning.set(false)
                // Send a close frame in response
                sendFrame(output, fin = true, opcode = 0x8, payloadData = Array.emptyByteArray)
              case 0x9 => // Ping frame
                // println("Received ping frame")
                // Send a pong frame in response
                sendFrame(output, fin = true, opcode = 0xA, payloadData = frame.payloadData)
              case 0xA => // Pong frame
                // println("Received pong frame")
              case _ =>
                // println(s"Received unsupported frame with opcode ${frame.opcode}")
          case None =>
            // println("Client closed the connection")
            isRunning.set(false)
    catch
      case e: EOFException =>
        println("Client disconnected")
      case e: Exception =>
        println(s"Error during communication: ${e.getMessage}")
    // finally
    //   println("Closing connection")

  private def readFrame(input: InputStream): Option[WebSocketFrame] =
    // Read the first two bytes of the frame header
    val b1 = input.read()
    val b2 = input.read()

    if b1 == -1 || b2 == -1 then
      return None

    val fin = (b1 & 0x80) != 0
    val opcode = b1 & 0x0F
    val masked = (b2 & 0x80) != 0
    val payloadLenIndicator = b2 & 0x7F

    if !masked then
      throw new IOException("Client data must be masked")

    // Determine the payload length
    val payloadLen = payloadLenIndicator match
      case 126 =>
        // Next 2 bytes are the payload length
        val len1 = input.read()
        val len2 = input.read()
        if len1 == -1 || len2 == -1 then throw new EOFException()
        ((len1 << 8) | len2).toLong
      case 127 =>
        // Next 8 bytes are the payload length
        val lengths = Array.fill(8)(input.read())
        if lengths.contains(-1) then throw new EOFException()
        lengths.foldLeft(0L)((acc, b) => (acc << 8) | b)
      case len =>
        len.toLong

    // Read the masking key (4 bytes)
    val mask = Array.fill(4)(input.read())

    // Read the payload data
    val payloadData = new Array[Byte](payloadLen.toInt)
    var totalRead = 0
    while totalRead < payloadLen do
      val read = input.read(payloadData, totalRead, (payloadLen - totalRead).toInt)
      if read == -1 then throw new EOFException()
      totalRead += read

    // Unmask the payload data
    for i <- payloadData.indices do
      payloadData(i) = (payloadData(i) ^ mask(i % 4)).toByte

    Some(WebSocketFrame(fin, opcode, masked, payloadData))

  private def sendFrame(
      output: OutputStream,
      fin: Boolean,
      opcode: Int,
      payloadData: Array[Byte]
  ): Unit =
    val b1 = ((if fin then 0x80 else 0x0) | (opcode & 0x0F)).toByte
    val maskBit = 0x00 // Server-to-client frames are not masked
    val payloadLen = payloadData.length

    val header = payloadLen match
      case len if len <= 125 =>
        Array[Byte](b1, (maskBit | len).toByte)
      case len if len <= 65535 =>
        val lenBytes = Array(
          ((len >> 8) & 0xFF).toByte,
          (len & 0xFF).toByte
        )
        Array[Byte](b1, (maskBit | 126).toByte) ++ lenBytes
      case len =>
        val lenBytes = (0 to 7).reverse.map(i => ((len >> (8 * i)) & 0xFF).toByte).toArray
        Array[Byte](b1, (maskBit | 127).toByte) ++ lenBytes

    // Write the frame header and payload
    output.write(header)
    output.write(payloadData)
    output.flush()

// Data class to represent a WebSocket frame
case class WebSocketFrame(
    fin: Boolean,
    opcode: Int,
    masked: Boolean,
    payloadData: Array[Byte]
)
