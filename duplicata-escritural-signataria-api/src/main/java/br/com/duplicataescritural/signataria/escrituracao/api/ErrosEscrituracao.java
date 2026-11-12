package br.com.duplicataescritural.signataria.escrituracao.api;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;

import br.com.duplicataescritural.signataria.escrituracao.dominio.ConflitoIdempotencia;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = EscrituracaoController.class)
public class ErrosEscrituracao {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail validacao(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
    }

    @ExceptionHandler(ConflitoIdempotencia.class)
    ProblemDetail conflito(ConflitoIdempotencia exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail jsonInvalido(HttpMessageNotReadableException exception) {
        if (exception.getMostSpecificCause() instanceof IllegalArgumentException validacao) {
            return validacao(validacao);
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "JSON inválido ou campo não permitido");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail resposta(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ProblemDetail falhaPersistencia(RuntimeException exception) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        for (Throwable causa = exception; causa != null; causa = causa.getCause()) {
            if (causa instanceof TransientDataAccessException || causa instanceof RecoverableDataAccessException
                    || causa instanceof TransactionTimedOutException || causa instanceof SQLTransientException
                    || causa instanceof SQLRecoverableException) {
                status = HttpStatus.SERVICE_UNAVAILABLE;
                break;
            }
        }
        return ProblemDetail.forStatusAndDetail(status, "Não foi possível confirmar a operação");
    }
}
