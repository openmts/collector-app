package com.openvehicletracking.collector.http.filter;

import com.openvehicletracking.collector.AppConstants;
import com.openvehicletracking.collector.db.MongoCollection;
import com.openvehicletracking.collector.db.Query;
import com.openvehicletracking.collector.helper.HttpHelper;
import com.openvehicletracking.collector.http.domain.AccessToken;
import com.openvehicletracking.collector.http.domain.User;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by oksuz on 09/07/2017.
 *
 */
public class AuthorizationFilter implements Handler<RoutingContext> {

    private HashSet<String> preAuthPaths = new HashSet<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationFilter.class);

    private AuthorizationFilter(HashSet<String> preAuthPaths) {
        this.preAuthPaths.addAll(preAuthPaths);
    }

    public static AuthorizationFilter create(HashSet<String> preAuthPaths) {
        return new AuthorizationFilter(preAuthPaths);
    }

    @Override
    public void handle(final RoutingContext context) {
        final HttpServerRequest request = context.request();
        final HttpServerResponse response = context.response();

        Optional<String> requestPath = preAuthPaths.stream().filter(s -> {
            Pattern p = Pattern.compile(s);
            Matcher m = p.matcher(request.path());
            return m.matches();
        }).findAny();

        if (requestPath.isPresent()) {
            LOGGER.debug("Path {} is pre authorised", request.path());
            context.next();
            return;
        }

        if (request.getHeader(AppConstants.HEADER_ACCESS_TOKEN) == null || Objects.equals("", request.getHeader(AppConstants.HEADER_ACCESS_TOKEN))) {
            HttpHelper.getUnauthorized(response).end();
            LOGGER.debug("access token header is empty");
            return;
        }

        String accessToken = request.getHeader(AppConstants.HEADER_ACCESS_TOKEN);
        Query accessTokenQuery = new Query(MongoCollection.ACCESS_TOKENS)
                .addCondition("token", accessToken)
                .setFindOne(true);

        context.vertx().eventBus().<JsonObject>send(AppConstants.Events.NEW_QUERY, accessTokenQuery, accessTokenResult -> {
            if (accessTokenResult.failed()) {
                LOGGER.error("access token query failed", accessTokenResult.cause());
                HttpHelper.getInternalServerError(response, accessTokenResult.cause().getMessage()).end();
                return;
            }

            JsonObject accessTokenQueryResult = accessTokenResult.result().body();
            if (accessTokenQueryResult == null) {
                HttpHelper.getUnauthorized(response).end();
                return;
            }

            Query userQuery = new Query(MongoCollection.USERS)
                    .addCondition("email", accessTokenQueryResult.getString("email"))
                    .setFindOne(true);

            context.vertx().eventBus().<JsonObject>send(AppConstants.Events.NEW_QUERY, userQuery, result -> {
                if (result.failed()) {
                    LOGGER.error("user query failed", result.cause());
                    HttpHelper.getInternalServerError(response, result.cause().getMessage()).end();
                    return;
                }

                JsonObject userResult = result.result().body();
                if (userResult == null) {
                    HttpHelper.getUnauthorized(response).end();
                    return;
                }

                User user = User.fromMongoRecord(userResult);

                context.put("user", user);
                context.next();
            });
        });
    }
}