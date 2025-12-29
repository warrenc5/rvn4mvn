package rvn;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.List;

import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyServer {

    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private final int port;
    private List<Pattern> blockedUrlPattern;

    public ProxyServer(int port) {
        this.port = port;
    }

    public void updateBlockedUrl(List<Pattern> blockedUrlPattern) {
        this.blockedUrlPattern = blockedUrlPattern;
    }

    public void start() throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        SslContext sslCtx = SslContext.newServerContext(ssc.certificate(), ssc.privateKey());

        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ProxyServerInitializer(sslCtx))
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = b.bind(port).sync();
            System.out.println("Proxy server started on port " + port);
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    private class ProxyServerInitializer extends ChannelInitializer<SocketChannel> {

        private final SslContext sslCtx;

        ProxyServerInitializer(SslContext sslCtx) {
            this.sslCtx = sslCtx;
        }

        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline p = ch.pipeline();
            //SSLEngine sslEngine = sslCtx.newEngine(ch.alloc());
            //p.addLast(sslCtx.newHandler(ch.alloc()));
            //p.addLast(new SslHandler(sslEngine));
            p.addLast(new HttpRequestDecoder());
            p.addLast(new HttpResponseEncoder());
            p.addLast(new HttpObjectAggregator(65536));
            p.addLast(new ProxyServerHandler());
        }
    }

    private static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private class ProxyServerHandler extends SimpleChannelInboundHandler<HttpObject> {

        private volatile Channel outboundChannel;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.pipeline().addFirst(new IdleStateHandler(0, 0, 60));
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (outboundChannel != null) {
                closeOnFlush(outboundChannel);
            }
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
            if (outboundChannel == null) {
                if (msg instanceof HttpRequest) {
                    HttpRequest request = (HttpRequest) msg;
                    String url = request.uri();

                    log.info("*****" + url);
                    if (blockedUrlPattern.stream().anyMatch(p -> p.matcher(url).find())) {
                        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.EXPECTATION_FAILED,
                                Unpooled.copiedBuffer("Blocked URL: " + url, CharsetUtil.UTF_8));
                        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
                        response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, response.content().readableBytes());
                        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                        log.warn("blocked " + url);
                        return;
                    }

                    Bootstrap b = new Bootstrap();
                    b.group(ctx.channel().eventLoop())
                            .channel(ctx.channel().getClass())
                            .handler(new ProxyBackendHandler(ctx.channel()))
                            .option(ChannelOption.AUTO_READ, false);

                    int port = 0;
                    String host = request.headers().get(HttpHeaders.Names.HOST);

                    if (url.startsWith("http")) 
                    try {
                        port = new URL(url).getPort();
                        if(port == -1)  { 
                            if(url.startsWith("http://"))
                                port = 80;
                            else if(url.startsWith("https://"))
                                port = 443;
                        }
                    } catch (Exception x) {
                        log.warn(x.getMessage());
                    } else {
                        InetSocketAddress inet = InetSocketAddress.createUnresolved(url.split(":")[0], Integer.parseInt(url.split(":")[1]));
                        port = inet.getPort();
                    }
                    log.info("->" + host + " " + port);
                    ChannelFuture f = b.connect(host, port);
                    outboundChannel = f.channel();

                    f.addListener((ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            ctx.channel().read();
                        } else {
                            ctx.close();
                        }
                    });
                }
            } else {
                outboundChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        ctx.channel().read();
                    } else {
                        future.channel().close();
                    }
                });
            }
        }

        protected void messageReceived(ChannelHandlerContext chc, HttpObject i) throws Exception {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }
    }

    class ProxyBackendHandler extends ChannelInboundHandlerAdapter {

        private final Channel clientChannel;

        public ProxyBackendHandler(Channel clientChannel) {
            this.clientChannel = clientChannel;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof HttpResponse) {
                HttpResponse httpResponse = (HttpResponse) msg;
                // Prepare the response status line
                HttpResponseStatus status = httpResponse.status();
                String version = httpResponse.protocolVersion().text();
                String statusLine = version + " " + status.code() + " " + status.reasonPhrase();

                // Prepare the response headers
                HttpHeaders headers = httpResponse.headers();
                StringBuilder headerBuilder = new StringBuilder();
                for (String name : headers.names()) {
                    String value = headers.get(name);
                    headerBuilder.append(name).append(": ").append(value).append("\r\n");
                }

                // Combine the status line and headers and send them to the client
                String responseHeaders = statusLine + "\r\n" + headerBuilder.toString() + "\r\n";
                clientChannel.writeAndFlush(responseHeaders);

                // If it's the last chunk, send an empty chunk to terminate the response
                if (msg instanceof LastHttpContent) {
                    clientChannel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                }
            }

            if (msg instanceof HttpContent) {
                HttpContent httpContent = (HttpContent) msg;
                ByteBuf content = httpContent.content();

                // Send the content of the response to the client
                clientChannel.writeAndFlush(content);

                // If it's the last chunk, send an empty chunk to terminate the response
                if (msg instanceof LastHttpContent) {
                    clientChannel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                }
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (clientChannel.isActive()) {
                clientChannel.writeAndFlush(Unpooled.EMPTY_BUFFER)
                        .addListener(ChannelFutureListener.CLOSE);
            }
            ctx.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
