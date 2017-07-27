package com.openvehicletracking.collector.controller;

import com.openvehicletracking.collector.domain.MessageRequest;
import com.openvehicletracking.collector.helper.HttpHelper;
import com.openvehicletracking.collector.helper.MongoHelper;
import com.openvehicletracking.core.OpenVehicleTracker;
import com.openvehicletracking.core.db.Collection;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

import java.text.ParseException;
import java.util.List;

/**
 * Created by oksuz on 09/07/2017.
 */
abstract public class AbstractLastMessagesController extends AbstractController {

    public AbstractLastMessagesController(RoutingContext context) {
        super(context);
    }

    protected void lastMessages(RoutingContext ctx, Handler<AsyncResult<List<JsonObject>>> handler) {
        MessageRequest request;
        try {
            request = new MessageRequest(ctx.request());
        } catch (Exception e) {
            HttpHelper.getBadRequest(ctx.response(), e.getMessage()).end();
            return;
        }

        JsonObject user = routingContext.get("user");
        if (!user.getJsonArray("devices").contains(request.getDeviceId())) {
            HttpHelper.getUnauthorized(routingContext.response()).end();
            return;
        }

        MongoHelper.Query query;
        try {
            query = MongoHelper.getLastMessagesQuery(request.getSize(), request.getGpsStatus(), request.getDeviceId(), request.getFromDate(), request.getToDate());
        } catch (ParseException e) {
            HttpHelper.getBadRequest(ctx.response(), "invalid date format. date format must be " + MessageRequest.DATE_FORMAT).end();
            return;
        }

        MongoClient mongoClient = OpenVehicleTracker.getInstance().newDbClient();
        mongoClient.findWithOptions(Collection.MESSAGES, query.getQuery(), query.getFindOptions(), result -> {
            handler.handle(result);
            mongoClient.close();
        });
    }

}