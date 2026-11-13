package br.com.duplicataescritural.financiamento.consultarecebiveis.integracao;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.Span;

// Receptor temporário, sem armazenamento externo; não faz parte de nenhuma aplicação.
final class ReceptorOtlp implements AutoCloseable {
    record Recebido(String servico, Span span) { }
    final List<Recebido> spans = new CopyOnWriteArrayList<>();
    private final HttpServer servidor;
    private final java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();

    ReceptorOtlp() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.setExecutor(executor);
        servidor.createContext("/v1/traces", exchange -> {
            try (exchange) {
                var pedido = ExportTraceServiceRequest.parseFrom(exchange.getRequestBody());
                for (var recurso : pedido.getResourceSpansList()) {
                    String servico = recurso.getResource().getAttributesList().stream()
                            .filter(a -> a.getKey().equals("service.name"))
                            .map(a -> a.getValue().getStringValue()).findFirst().orElse("");
                    recurso.getScopeSpansList().forEach(scope -> scope.getSpansList()
                            .forEach(span -> spans.add(new Recebido(servico, span))));
                }
                exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
                exchange.sendResponseHeaders(200, -1);
            }
        });
        servidor.start();
    }

    String endpoint() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1/traces";
    }

    @Override
    public void close() {
        servidor.stop(0);
        executor.shutdownNow();
    }
}
