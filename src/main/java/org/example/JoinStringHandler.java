package org.example;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;

public class JoinStringHandler implements Handler<RoutingContext> {
    
     public void handle(RoutingContext context) {
        parseStrings(context)
                .flatMap(strings -> performString(strings, this::joinString, "join string"))
                .subscribe(
                        result -> sendResponse(context, result),
                        error -> sendError(context, error)
                );
    }

    private Single<JsonObject> parseStrings(RoutingContext context) {
        return Single.fromCallable(() -> {
            JsonObject body = context.body().asJsonObject();
            if (body == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            String a = body.getString("a");
            String b = body.getString("b");

            if (a == null || b == null) {
                throw new IllegalArgumentException("Both 'a' and 'b' parameters are required");
            }

            return new JsonObject().put("a", a).put("b", b);
        });
    }

    private Single<JsonObject> performString(JsonObject strings, StringOperation operation, String operationName) {
        return Single.fromCallable(() -> {
            String a = strings.getString("a");
            String b = strings.getString("b");
            String result = operation.apply(a, b);

            return new JsonObject()
                    .put("input_a", a)
                    .put("input_b", b)
                    .put("operation", operationName)
                    .put("result", result)
                    .put("timestamp", System.currentTimeMillis());
        });
    }

    @FunctionalInterface
    private interface StringOperation {
        String apply(String a, String b);
    }

    public String joinString(String a, String b) {
        return a.concat(" ").concat(b);
    }


    private void sendResponse(RoutingContext context, JsonObject response) {
        context.response()
                .putHeader("content-type", "application/json")
                .setStatusCode(200)
                .end(response.encodePrettily());
    }

    private void sendError(RoutingContext context, Throwable error) {
        JsonObject errorResponse = new JsonObject()
                .put("error", error.getMessage())
                .put("timestamp", System.currentTimeMillis());

        int statusCode = 400;
        if (error instanceof ArithmeticException) {
            statusCode = 422; // Unprocessable Entity
        }

        System.err.println("send error: " + error.getMessage());

        context.response()
                .putHeader("content-type", "application/json")
                .setStatusCode(statusCode)
                .end(errorResponse.encodePrettily());
    }
}
