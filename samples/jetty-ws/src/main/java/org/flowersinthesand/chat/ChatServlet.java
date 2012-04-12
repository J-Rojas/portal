package org.flowersinthesand.chat;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketServlet;

import com.google.gson.Gson;

@WebServlet(urlPatterns = "/chat", asyncSupported = true)
public class ChatServlet extends WebSocketServlet {

	private static final long serialVersionUID = -9048812968644311964L;

	private Map<String, ChatWebSocket> connections = new ConcurrentHashMap<String, ChatWebSocket>();
	private BlockingQueue<Event> queue = new LinkedBlockingQueue<Event>();
	private Thread broadcaster = new Thread(new Runnable() {
		@Override
		public void run() {
			while (true) {
				try {
					Event event = queue.take();
					for (Entry<String, ChatWebSocket> entry : connections.entrySet()) {
						try {
							send(entry.getValue(), event);
						} catch (IOException ex) {
							Event e = new Event();
							e.socket = entry.getKey();
							e.type = "close";

							fire(e);
						}
					}
				} catch (InterruptedException e) {
					break;
				}
			}
		}
	});

	@Override
	public void init() throws ServletException {
		super.init();
		broadcaster.setDaemon(true);
		broadcaster.start();
	}

	@Override
	public WebSocket doWebSocketConnect(HttpServletRequest request, String protocol) {
		return new ChatWebSocket(request.getParameter("id"));
	}

	private void send(ChatWebSocket webSocket, Event event) throws IOException {
		String data = new Gson().toJson(event);
		webSocket.connection.sendMessage(data);
	}

	private void fire(Event event) {
		if (event.type.equals("close")) {
			connections.remove(event.socket);
		}

		handle(event);
	}

	private void handle(Event event) {
		if (event.type.equals("message")) {
			queue.offer(new Event("message", event.data));
		}
	}

	private class ChatWebSocket implements WebSocket.OnTextMessage {
		String id;
		Connection connection;

		public ChatWebSocket(String id) {
			this.id = id;
		}

		@Override
		public void onOpen(Connection connection) {
			this.connection = connection;
			connections.put(id, this);

			Event e = new Event();
			e.socket = id;
			e.type = "open";

			fire(e);
		}

		@Override
		public void onClose(int closeCode, String message) {
			Event e = new Event();
			e.socket = id;
			e.type = "close";

			fire(e);
		}

		@Override
		public void onMessage(String data) {
			fire(new Gson().fromJson(data, Event.class));
		}
	}

	private static class Event {
		private String socket;
		private String type;
		private Object data;

		public Event() {

		}

		public Event(String type, Object data) {
			this.type = type;
			this.data = data;
		}
	}

}