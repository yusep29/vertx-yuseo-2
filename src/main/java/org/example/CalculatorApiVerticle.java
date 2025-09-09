package org.example;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.Observable;

public class CalculatorApiVerticle extends AbstractVerticle {

    @Override
    public void start() {
        Router router = Router.router(vertx);

        // Enable body parsing for JSON payloads
        router.route().handler(BodyHandler.create());

        // Routes for different operations
        router.post("/api/calculate/add").handler(this::addHandler);
        router.post("/api/calculate/subtract").handler(this::subtractHandler);
        router.post("/api/calculate/multiply").handler(this::multiplyHandler);
        router.post("/api/calculate/divide").handler(this::divideHandler);
        router.post("/api/calculate/power").handler(this::powerHandler);
        router.post("/api/calculate/sqrt").handler(this::sqrtHandler);
        router.post("/api/calculate/batch").handler(this::batchCalculationHandler);
        router.post("/api/string/join").handler(new JoinStringHandler());
        router.post("/api/string/replace").handler(this::replaceStringHandler);
        router.post("/api/trx/submit").handler(new TestMapHandler());

        // Health check endpoint
        router.get("/api/health").handler(this::healthHandler);

        int port = 1080;
        // Start the HTTP server
        vertx.createHttpServer()
                .requestHandler(router)
                .rxListen(port)
                .subscribe(
                        server -> System.out.println("Calculator API started on port " + port),
                        error -> System.err.println("Failed to start server: " + error.getMessage())
                );
    }

    private void addHandler(RoutingContext context) {
        parseNumbers(context)
                .flatMap(numbers -> performCalculation(numbers, Double::sum, "addition"))
                .subscribe(
                        result -> sendResponse(context, result),
                        error -> sendError(context, error)
                );
    }

    private void subtractHandler(RoutingContext context) {
        parseNumbers(context)
                .flatMap(numbers -> performCalculation(numbers, (a, b) -> a - b, "subtraction"))
                .subscribe(
                        result -> sendResponse(context, result),
                        error -> sendError(context, error)
                );
    }

    private void multiplyHandler(RoutingContext context) {
        parseNumbers(context)
                .flatMap(numbers -> performCalculation(numbers, (a, b) -> a * b, "multiplication"))
                .subscribe(
                        result -> sendResponse(context, result),
                        error -> sendError(context, error)
                );
    }

    private void divideHandler(RoutingContext context) {
        parseNumbers(context)
                .flatMap(numbers -> {
                    if (numbers.getDouble("b") == 0) {
                        return Single.error(new ArithmeticException("Division by zero"));
                    }
                    return performCalculation(numbers, (a, b) -> a / b, "division");
                })
                .subscribe(
                        result -> sendResponse(context, result),
                        error -> sendError(context, error)
                );
    }

    private void powerHandler(RoutingContext context) {
        parseNumbers(context)
                .flatMap(numbers -> performCalculation(numbers, Math::pow, "power"))
                .subscribe(
                        result -> sendResponse(context, result),
                        error -> sendError(context, error)
                );
    }

    private void sqrtHandler(RoutingContext context) {
        parseSingleNumber(context)
                .flatMap(number -> {
                    if (number < 0) {
                        return Single.error(new ArithmeticException("Cannot calculate square root of negative number"));
                    }
                    double result = Math.sqrt(number);
                    return Single.just(new JsonObject()
                            .put("input", number)
                            .put("operation", "square_root")
                            .put("result", result)
                            .put("timestamp", System.currentTimeMillis()));
                })
                .subscribe(
                        result -> sendResponse(context, result),
                        error -> sendError(context, error)
                );
    }

    private void batchCalculationHandler(RoutingContext context) {
        try {
            JsonObject body = context.body().asJsonObject();
            if (body == null || !body.containsKey("operations")) {
                sendError(context, new IllegalArgumentException("Invalid request format. Expected 'operations' array."));
                return;
            }

            Observable.fromIterable(body.getJsonArray("operations"))
                    .cast(JsonObject.class)
                    .flatMapSingle(this::processBatchOperation)
                    .toList()
                    .subscribe(
                            results -> {
                                JsonObject response = new JsonObject()
                                        .put("results", results)
                                        .put("timestamp", System.currentTimeMillis());
                                sendResponse(context, response);
                            },
                            error -> sendError(context, error)
                    );

        } catch (Exception e) {
            sendError(context, e);
        }
    }

//    private void joinStringHandler(RoutingContext context) {
//        parseStrings(context)
//                .flatMap(strings -> performString(strings, this::joinString, "join string"))
//                .subscribe(
//                        result -> sendResponse(context, result),
//                        error -> sendError(context, error)
//                );
//    }

    private void replaceStringHandler(RoutingContext context) {
        parseStrings(context)
                .flatMap(strings -> performString(strings, this::replaceString, "replace string"))
                .subscribe(
                        result -> sendResponse(context, result),
                        error -> sendError(context, error)
                );
    }

    private Single<JsonObject> processBatchOperation(JsonObject operation) {
        String op = operation.getString("operation");
        Double a = operation.getDouble("a");
        Double b = operation.getDouble("b");

        if (op == null || a == null) {
            return Single.error(new IllegalArgumentException("Invalid operation format"));
        }

        return Single.fromCallable(() -> {
            double result;
            switch (op.toLowerCase()) {
                case "add":
                    result = a + (b != null ? b : 0);
                    break;
                case "subtract":
                    result = a - (b != null ? b : 0);
                    break;
                case "multiply":
                    result = a * (b != null ? b : 1);
                    break;
                case "divide":
                    if (b == null || b == 0) {
                        throw new ArithmeticException("Division by zero");
                    }
                    result = a / b;
                    break;
                case "power":
                    result = Math.pow(a, b != null ? b : 2);
                    break;
                case "sqrt":
                    if (a < 0) {
                        throw new ArithmeticException("Cannot calculate square root of negative number");
                    }
                    result = Math.sqrt(a);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported operation: " + op);
            }

            return new JsonObject()
                    .put("operation", op)
                    .put("input_a", a)
                    .put("input_b", b)
                    .put("result", result);
        });
    }

    private void healthHandler(RoutingContext context) {
        JsonObject health = new JsonObject()
                .put("status", "UP")
                .put("service", "Calculator API")
                .put("timestamp", System.currentTimeMillis());
        sendResponse(context, health);
    }

    private Single<JsonObject> parseNumbers(RoutingContext context) {
        return Single.fromCallable(() -> {
            JsonObject body = context.body().asJsonObject();
            if (body == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Double a = body.getDouble("a");
            Double b = body.getDouble("b");

            if (a == null || b == null) {
                throw new IllegalArgumentException("Both 'a' and 'b' parameters are required");
            }

            return new JsonObject().put("a", a).put("b", b);
        });
    }

    private Single<Double> parseSingleNumber(RoutingContext context) {
        return Single.fromCallable(() -> {
            JsonObject body = context.body().asJsonObject();
            if (body == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Double value = body.getDouble("value");
            if (value == null) {
                throw new IllegalArgumentException("'value' parameter is required");
            }

            return value;
        });
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

    private Single<JsonObject> performCalculation(JsonObject numbers, BinaryOperation operation, String operationName) {
        return Single.fromCallable(() -> {
            double a = numbers.getDouble("a");
            double b = numbers.getDouble("b");
            double result = operation.apply(a, b);

            return new JsonObject()
                    .put("input_a", a)
                    .put("input_b", b)
                    .put("operation", operationName)
                    .put("result", result)
                    .put("timestamp", System.currentTimeMillis());
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

    @FunctionalInterface
    private interface BinaryOperation {
        double apply(double a, double b);
    }

    @FunctionalInterface
    private interface StringOperation {
        String apply(String a, String b);
    }
    public String joinString(String a, String b) {
        return a.concat(" ").concat(b);
    }

    public String replaceString(String a, String b) {
        String replacer = "";
        for(int i=0;i<b.length();i++){
            replacer = replacer.concat("*");
        }
        return a.replace(b,replacer);
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.rxDeployVerticle(new CalculatorApiVerticle())
                .subscribe(
                        id -> System.out.println("Calculator API deployed with ID: " + id),
                        error -> {
                            System.err.println("Failed to deploy Calculator API: " + error.getMessage());
                            vertx.close();
                        }
                );
    }
}