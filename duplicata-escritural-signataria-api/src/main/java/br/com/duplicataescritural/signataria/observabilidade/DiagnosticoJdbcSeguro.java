package br.com.duplicataescritural.signataria.observabilidade;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.core.env.Environment;

// Preserva a lista permitida de diagnósticos JDBC anterior, agora em JSON e sem throwable.
public class DiagnosticoJdbcSeguro implements StructuredLogFormatter<ILoggingEvent> {
    private final JsonWriter<ILoggingEvent> json;

    public DiagnosticoJdbcSeguro(Environment ambiente) {
        json = JsonWriter.of(membros -> {
            membros.add("@timestamp", evento -> evento.getInstant().toString());
            membros.add("level", evento -> evento.getLevel().toString());
            membros.add("service", ambiente.getProperty("spring.application.name"));
            membros.add("logger_name", ILoggingEvent::getLoggerName);
            membros.add("message", evento -> evento.getFormattedMessage()
                    .matches("HHH000247: ErrorCode: -?[0-9]+, SQLState: (?:[A-Z0-9]{5}|null)")
                    ? evento.getFormattedMessage() : "Detalhes JDBC omitidos por segurança");
            membros.add("traceId", evento -> evento.getMDCPropertyMap().get("traceId")).whenNotNull();
            membros.add("spanId", evento -> evento.getMDCPropertyMap().get("spanId")).whenNotNull();
        });
    }

    @Override
    public String format(ILoggingEvent evento) {
        return json.writeToString(evento) + "\n";
    }
}
