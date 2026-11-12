package br.com.duplicataescritural.signataria.consultarecebiveis.api;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;

import br.com.duplicataescritural.signataria.consultarecebiveis.aplicacao.RecursoConsultaNaoEncontrado;
import br.com.duplicataescritural.signataria.consultarecebiveis.dominio.ConflitoAutorizacao;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = AutorizacaoConsultaController.class)
public class ErrosAutorizacaoConsulta {
    @ExceptionHandler(ConflitoAutorizacao.class)
    ProblemDetail conflito(ConflitoAutorizacao exception) {
        return erro(HttpStatus.CONFLICT, exception.codigo(), "Operação incompatível com a autorização de consulta");
    }

    @ExceptionHandler(RecursoConsultaNaoEncontrado.class)
    ProblemDetail naoEncontrado() {
        return erro(HttpStatus.NOT_FOUND, "RECURSO_NAO_ENCONTRADO", "Recurso não encontrado");
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ProblemDetail validacao() {
        return erro(HttpStatus.UNPROCESSABLE_CONTENT, "DADOS_INVALIDOS", "Dados inválidos para autorização de consulta");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail requisicaoInvalida() {
        return erro(HttpStatus.BAD_REQUEST, "REQUISICAO_INVALIDA", "Requisição inválida ou campo não permitido");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail chaveInvalida() {
        return erro(HttpStatus.BAD_REQUEST, "CHAVE_IDEMPOTENCIA_INVALIDA", "Idempotency-Key inválida ou ausente");
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ProblemDetail persistencia(RuntimeException exception) {
        for (Throwable causa = exception; causa != null; causa = causa.getCause()) {
            if (causa instanceof TransientDataAccessException || causa instanceof RecoverableDataAccessException
                    || causa instanceof TransactionTimedOutException || causa instanceof SQLTransientException
                    || causa instanceof SQLRecoverableException) {
                return erro(HttpStatus.SERVICE_UNAVAILABLE, "INDISPONIBILIDADE_TEMPORARIA", "Não foi possível confirmar a operação");
            }
        }
        return erro(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO", "Não foi possível confirmar a operação");
    }

    private ProblemDetail erro(HttpStatus status, String codigo, String mensagem) {
        var resposta = ProblemDetail.forStatusAndDetail(status, mensagem);
        resposta.setProperty("codigo", codigo);
        return resposta;
    }
}
