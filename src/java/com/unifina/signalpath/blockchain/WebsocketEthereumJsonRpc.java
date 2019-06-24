package com.unifina.signalpath.blockchain;

import com.google.gson.JsonParser;
import org.apache.log4j.Logger;
import javax.websocket.*;
import javax.websocket.Session;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.json.JSONObject;

public class WebsocketEthereumJsonRpc extends EthereumJsonRpc {
	private static final Logger log = Logger.getLogger(WebsocketEthereumJsonRpc.class);
	private EthereumRpcEndpoint wsEndpoint;
	private long attemptReconnectionWaittimeMs = 60000;
	private long asyncTimeoutMs = 60000;
	private int attemptReconnectionMaxTries = -1;
	private Session userSession = null;
	private boolean reconnectOnError = true;

	public long getAttemptReconnectionWaittimeMs() {
		return attemptReconnectionWaittimeMs;
	}

	public void setAttemptReconnectionWaittimeMs(long attemptReconnectionWaittimeMs) {
		this.attemptReconnectionWaittimeMs = attemptReconnectionWaittimeMs;
	}

	public long getAsyncTimeoutMs() {
		return asyncTimeoutMs;
	}

	public void setAsyncTimeoutMs(long asyncTimeoutMs) {
		this.asyncTimeoutMs = asyncTimeoutMs;
	}

	public int getAttemptReconnectionMaxTries() {
		return attemptReconnectionMaxTries;
	}

	public void setAttemptReconnectionMaxTries(int attemptReconnectionMaxTries) {
		this.attemptReconnectionMaxTries = attemptReconnectionMaxTries;
	}



	@ClientEndpoint
	protected class EthereumRpcEndpoint{

		@OnClose
		public void onClose(Session session, CloseReason reason){
			CloseReason.CloseCode code = reason.getCloseCode();
			log.info("session "+session + "was closed. CloseReason: "+ reason);
			if(reconnectOnError && code.getCode() != CloseReason.CloseCodes.NORMAL_CLOSURE.getCode() ) {
				try {
					log.info("Trying reconnect");
					if (!openConnectionRetryIfFail()) {
						String err = "Couldnt reestablish connection to " + url;
						throw new RuntimeException(err);
					}
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}
		@OnError
		public void onError(Session session, Throwable t){
			log.error("session "+session+ " reported error "+t.getMessage());
			if(reconnectOnError) {
				try {
					log.info("Trying reconnect");
					if (!openConnectionRetryIfFail()) {
						String err = "Couldnt reestablish connection to " + url;
						throw new RuntimeException(err);
					}
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}

		@OnOpen
		public void onOpen(Session session) {
			log.info("opening websocket "+session);
		}

		@OnMessage
		public void onMessage(String message) {
			JSONObject jso = new JSONObject(message);
			handler.processResponse(jso);
		}


		public void sendMessage(String message) {
			userSession.getAsyncRemote().sendText(message);
		}
	}

	public WebsocketEthereumJsonRpc(String url, JsonRpcResponseHandler handler){
		super(url,handler);
		wsEndpoint = new EthereumRpcEndpoint();
	}

	public boolean openConnectionRetryIfFail() throws InterruptedException {
		int attempts=0;
		while(attemptReconnectionMaxTries < 0 || attempts < attemptReconnectionMaxTries){
			attempts++;
			log.info("Trying to establish websocket connection to "+url+". Attempt number "+ attempts);
			try {
				openConnection();
			} catch (URISyntaxException e) {
				log.error(e);
				return false;
			} catch (IOException | DeploymentException e) {
				log.error(e);
				log.info("Waiting "+attemptReconnectionWaittimeMs+"ms");
				Thread.sleep(attemptReconnectionWaittimeMs);
				continue;
			}
			return true;
		}
		log.error("Couldnt establish connection to " + url + " after "+attempts +" attempts. Aborting.");
		return false;
	}

	@Override
	public void rpcCall(String method, List params, int callId){
		wsEndpoint.sendMessage(formRequestBody(method, params, callId));
	}


	public void openConnection() throws URISyntaxException, IOException, DeploymentException {
		WebSocketContainer container = ContainerProvider.getWebSocketContainer();
		container.setAsyncSendTimeout(asyncTimeoutMs);
		try {
			userSession = container.connectToServer(wsEndpoint, new URI(url));
		}
		catch(Exception e){
			e.printStackTrace();
			throw e;
		}
		log.info("Websocket connection established to "+url);
	}

	public void close() throws IOException {
		userSession.close();
	}
}

