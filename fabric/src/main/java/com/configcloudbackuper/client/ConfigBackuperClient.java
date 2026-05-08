package com.configcloudbackuper.client;

import com.configcloudbackuper.config.widget.ServerRemoteActionsEntry;
import com.configcloudbackuper.server.ServerSyncNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class ConfigBackuperClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(
                ServerSyncNetworking.ServerActionResultPayload.ID,
                ServerSyncNetworking.ServerActionResultPayload.CODEC
        );
        ClientPlayNetworking.registerGlobalReceiver(ServerSyncNetworking.ServerActionResultPayload.ID, (payload, context) ->
                context.client().execute(() -> ServerRemoteActionsEntry.updateFromServerResult(
                        payload.action(),
                        payload.success(),
                        payload.lines()
                )));
        ClientCommandRegistrationCallback.EVENT.register(ConfigBackuperCommands::register);
    }
}
