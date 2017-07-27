package com.openvehicletracking.collector.filter;

import com.openvehicletracking.collector.helper.HttpHelper;
import com.openvehicletracking.core.OpenVehicleTracker;
import com.openvehicletracking.core.db.Collection;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.Objects;

/**
 * Created by yo on 09/07/2017.
 */
public class AuthorizationFilter implements Handler<RoutingContext> {

    private List<String> allowedPaths;

    private AuthorizationFilter(List<String> allowedPaths) {
        this.allowedPaths = allowedPaths;
    }

    public static AuthorizationFilter create(List<String> allowedPaths) {
        return new AuthorizationFilter(allowedPaths);
    }

    @Override
    public void handle(RoutingContext context) {
        HttpServerRequest request = context.request();
        HttpServerResponse response = context.response();

        if (allowedPaths.contains(request.path())) {
            context.next();
            return;
        }

        if (request.getHeader("x-access-token") == null || Objects.equals("", request.getHeader("x-access-token"))) {
            HttpHelper.getUnauthorized(response).end();
            return;
        }

        String accessToken = request.getHeader("x-access-token");
        MongoClient client = OpenVehicleTracker.getInstance().newDbClient();
        JsonObject query = new JsonObject().put("accessToken", accessToken);

        client.find(Collection.USERS, query, result -> {
           if (result.succeeded() && result.result() != null && result.result().size() > 0) {
               context.put("user", result.result().get(0));
               context.next();
           } else {
               HttpHelper.getUnauthorized(response).end();
           }
        });
    }
}