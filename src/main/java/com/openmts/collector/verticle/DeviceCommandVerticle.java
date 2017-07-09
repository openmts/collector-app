package com.openmts.collector.verticle;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import com.openmts.core.Motodev;
import com.openmts.core.MotodevAbstractVerticle;
import com.openmts.core.db.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by oksuz on 07/03/2017.
 */
public class DeviceCommandVerticle extends MotodevAbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceCommandVerticle.class);

    @Override
    public void start() throws Exception {
        LOGGER.info("Starting verticle " + DeviceCommandVerticle.class.getName() );
        EventBus eventBus = vertx.eventBus();

        MessageConsumer<JsonObject> handler = eventBus.consumer(Motodev.Constant.DEVICE_COMMAND);
        handler.handler(messageHandler());
    }

    private Handler<Message<JsonObject>> messageHandler() {
        return message -> {
            JsonObject body = message.body();

            MongoClient mongoClient = MongoClient.createNonShared(vertx, config().getJsonObject("database").getJsonObject("mongodb"));
            JsonObject query = new JsonObject().put("deviceId", body.getString("deviceId")).put("read", false);

            mongoClient.find(Collection.COMMANDS, query, result -> {
                if (result.succeeded()) {
                    JsonArray reply = new JsonArray();
                    result.result().forEach(record -> {
                        String command = record.getString("command", "");
                        if (!"".equals(command)) {
                            reply.add(command);
                        }
                    });

                    if (reply.size() > 0) {
                        message.reply(reply);
                    }

                }

                mongoClient.close();
            });
        };
    }
}