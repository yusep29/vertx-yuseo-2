package org.example;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.Objects;

public class TestMapHandler implements Handler<RoutingContext> {
    
    private static String reqIdEnum = "reqId";
    private static String successEnum = "success";
    
    @Override
    public void handle(RoutingContext routingContext) {
        getRequest(routingContext)
                .concatMap(this::isRequestValid)
                .concatMap(this::processRequest)
                .subscribe(
                        response -> finalResponse(routingContext, response),
                        error -> badRequestResponse(routingContext, error)
                );
                
                
        
    }

    /** handling bad request only */
    private static Completable badRequestResponse(RoutingContext routingContext, Throwable error) {
        
        error.printStackTrace();
        
        String resp = buildFailedJsonObj("99", new JsonObject()).encodePrettily();
        System.out.println("response: " + resp);
        
        return routingContext.response()
                .putHeader("content-type", "application/json")
                .setStatusCode(400)
                .end(resp);
    }

    private static Completable finalResponse(RoutingContext routingContext, JsonObject response) {
        System.out.println("response body: " + response.encodePrettily());
        
        return routingContext.response()
                .putHeader("content-type", "application/json")
                .setStatusCode(200)
                .end(response.encodePrettily());
    }

    private Observable<JsonObject> processRequest(JsonObject request) {
        return getData(request)
                .concatMap(this::hitApiClient)
                .concatMap(response -> processResponseApiClient(response, request))
                .onErrorResumeNext(err -> handlingErrorInProcess(err, request));
    }

    private Observable<JsonObject> handlingErrorInProcess(Throwable err, JsonObject request) {
        
        err.printStackTrace();
        
        if (err.getMessage().equals("err1")){
            return buildResponseFailed("01", request);
        } else if (err.getMessage().equals("err2")){
            return buildResponseFailed("02", request);
        } else {
            return buildResponseFailed("00", request);
        }
    }

    private Observable<JsonObject> processResponseApiClient(String response, JsonObject request) {
        if (Objects.equals(response, successEnum)) {
            String responseMsg = "ini sukses mantap";
            return buildResponseSuccess(responseMsg, request);
        } else {
            return buildResponseFailed("01", request);
        }
    }

    private Observable<JsonObject> buildResponseSuccess(String response, JsonObject request) {
        return Observable.fromCallable(()->{
            JsonObject resp = new JsonObject();
            resp.put(reqIdEnum, request.getString(reqIdEnum));
            resp.put("status",successEnum);
            resp.put("data",response);
            return resp;
        });
    }

    private Observable<String> hitApiClient(JsonObject requestToApi) {
        return clientApi(requestToApi);
    }

    private Observable<String> clientApi(JsonObject requestToApi) {
        return Observable.just(requestToApi)
                .concatMap( x -> {
                    if (x.getString("data").equals("10")){
                        return Observable.just(successEnum);
                    } else {
                        return Observable.just("failed");
                    }
                });
    }

    private Observable<JsonObject> getData(JsonObject request){
        return getDataFromDb(request)
                .concatMap(this::formatingData);
    }
    
    private Observable<JsonObject> formatingData (String data){
        return Observable.fromCallable(()->{
            JsonObject dataJson = new JsonObject();
            dataJson.put("data",data);
            return dataJson;
        });
    }

    private Observable<String> getDataFromDb(JsonObject request) {
        return Observable.fromCallable(()->
        {
            if (request.getString("a").equals("sayang")) {
                return "10";
            } else if (request.getString("a").equals("sayange")) {
                throw new Exception("err1");
            } else if (request.getString("a").equals("sayangx")) {
                throw new Exception("err2");
            } else {
                return "11";
            }
        }
        );
    }

    private Observable<JsonObject> buildResponseFailed(String errCode, JsonObject request) {
        return Observable.fromCallable(()-> buildFailedJsonObj(errCode, request));
    }

    private static JsonObject buildFailedJsonObj(String errCode, JsonObject request) {
        JsonObject resp = new JsonObject();
        resp.put(reqIdEnum,request.getString(reqIdEnum));
        resp.put("status","failed");
        resp.put("errCode", errCode);
        return resp;
    }

    private Observable<JsonObject> getRequest(RoutingContext routingContext){
        return Observable.fromCallable(()->{
            JsonObject body = routingContext.body().asJsonObject();
            System.out.println("reqeust body: " + body.encodePrettily());
            
            if (body == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            String a = body.getString("a");
            String b = body.getString("b");
            String reqId =  body.getString(reqIdEnum);

            if (a == null || b == null) {
                throw new IllegalArgumentException("Both 'a' and 'b' parameters are required");
            }

            return new JsonObject().put("a", a).put("b", b).put(reqIdEnum, reqId);
        });
        
    }

    private Observable<JsonObject> isRequestValid(JsonObject request) {
        return Observable.fromCallable(()->{
            if (request.getString("a").length() > 1 && request.getString("b").length() > 1){
                return request;
            } else {
                throw new Exception("bad request");
            }
        });
    }
}
