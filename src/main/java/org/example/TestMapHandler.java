package org.example;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.Objects;

public class TestMapHandler implements Handler<RoutingContext> {
    
    private static String reqIdEnum = "reqId";
    private static String successEnum = "success";
    private final Scheduler vertxScheduler;

    public TestMapHandler(Scheduler vertxScheduler) {
        this.vertxScheduler = vertxScheduler;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        getRequest(routingContext)
                .concatMap(this::isRequestValid)
                .concatMap(this::processRequest)
                .subscribeOn(Schedulers.io())
                .observeOn(vertxScheduler)
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

    /** main process flow */
    private Observable<JsonObject> processRequest(JsonObject request) {
        return Observable.defer(()-> getData(request))
                .concatMap(dataFromDb -> isRequestCanBeProcess(dataFromDb, request))
                .concatMap(dataFromDb -> processPart1(dataFromDb, request))
                .concatMap(dataFromPart1 -> processPart2(dataFromPart1, request))
                .concatMap(result -> buildResponseSuccessFinal(result, request))
                .concatMap(this::sendNotification)
                .onErrorResumeNext(err -> handlingErrorInProcess(err, request));
    }
    
    /** process 1 */
    private Observable<JsonObject> processPart1(JsonObject dataFromDb, JsonObject request){
        return Observable.defer(()-> hitApiClient(dataFromDb))
                .concatMap(responseFromClient -> processResponseApiClient(responseFromClient, request))
                .concatMap(resp -> saveResult(resp, request))
                .onErrorResumeNext(err -> {throw new Exception("err4");});
    }

    /** process 2 - request data from process 1 */
    private Observable<JsonObject> processPart2(JsonObject dataFromPart1, JsonObject request){
        return Observable.defer(()-> hitApiClient2(dataFromPart1))
                .concatMap(responseFromClient -> processResponseApiClient(responseFromClient, request))
                .concatMap(resp -> saveResult(resp, request))
                .onErrorResumeNext(err -> {throw new Exception("err5");});
    }
    

    /** part notification. the process status is success even the notification not success */
    private Observable<JsonObject> sendNotification(JsonObject resp){
        return Observable.fromCallable(()-> resp)
                .doOnComplete(() ->{
                    Thread.sleep(5000);
                    notifyClient(resp)
                            .concatMap(x -> notifyMerchant(resp)).subscribe();
                });
    }

    private Observable<JsonObject> notifyClient(JsonObject resp) {
        return Observable.fromCallable(()->{
            if (false){
                throw new Exception("err3");
            }
            return resp;
        }).onErrorResumeNext(err -> {
            err.printStackTrace();
            return Observable.just(resp);
        });
    }

    private Observable<JsonObject> notifyMerchant(JsonObject resp) {
        return Observable.fromCallable(()->{
            if (false){
                throw new Exception("err3");
            }
            return resp;
        }).onErrorResumeNext(err -> {
            err.printStackTrace();
            return Observable.just(resp);
        });
    }

    private Observable<JsonObject> saveResult(JsonObject resp, JsonObject request) {
        return Observable.just(resp);
    }

    /** main error handler, convert all error itu response error */
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
            return buildResponseSuccessFromClientApi(responseMsg, request);
        } else {
            return buildResponseFailed("01", request);
        }
    }

    private Observable<JsonObject> buildResponseSuccessFromClientApi(String response, JsonObject request) {
        return Observable.fromCallable(()->{
            JsonObject resp = new JsonObject();
            resp.put(reqIdEnum, request.getString(reqIdEnum));
            resp.put("statusClient",successEnum);
            resp.put("data",response);
            return resp;
        });
    }

    /** main process to build response */
    private Observable<JsonObject> buildResponseSuccessFinal(JsonObject result, JsonObject request) {
        return Observable.fromCallable(()->{
            JsonObject resp = new JsonObject();
            resp.put(reqIdEnum, request.getString(reqIdEnum));
            resp.put("status",successEnum);
            resp.put("data",result);
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

    private Observable<String> hitApiClient2(JsonObject responseFromAPi1) {
        return clientApi2(responseFromAPi1);
    }

    private Observable<String> clientApi2(JsonObject responseFromAPi1) {
        return Observable.just(responseFromAPi1)
                .concatMap( x -> {
                    if (x.getString("statusClient").equals("success")){
                        return Observable.just(successEnum);
                    } else {
                        return Observable.just("failed");
                    }
                });
    }

    /** main process get data for processing and validate process is eligible or not*/
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

    /** first validation for request body. is format ok and mandatory param in request */
    private Observable<JsonObject> isRequestValid(JsonObject request) {
        return Observable.fromCallable(()->{
            if (request.getString("a").length() > 1 && request.getString("b").length() > 1){
                return request;
            } else {
                throw new Exception("bad request");
            }
        });
    }

    /** second validation for request body, validate user and condition transaction */
    private Observable<JsonObject> isRequestCanBeProcess(JsonObject dataFromDb, JsonObject request) {
        return Observable.fromCallable(()->{
            boolean isUserOk = true;
            boolean isTimeOk = true;
            boolean isConditionOk = dataFromDb != null;
            
            if (isUserOk && isTimeOk && isConditionOk && request != null){
                return dataFromDb;
            } else {
                throw new Exception("request can not be process");
            }
           
        });
    }
}
