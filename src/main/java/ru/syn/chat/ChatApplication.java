package ru.syn.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import ru.syn.chat.entity.Event;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class ChatApplication {

	@Bean
	public Sinks.Many<Event> eventPublisher() {
		return Sinks.many().unicast().onBackpressureBuffer();
	}

	@Bean
	public Flux<Event> events(Sinks.Many<Event> eventPublisher) {
		return eventPublisher
				.asFlux()
				.replay(25)
				.autoConnect();
	}

	@Bean
	public HandlerMapping webSocketMapping(Sinks.Many<Event> eventPublisher, Flux<Event> events) {
		Map<String, Object> map = new HashMap<>();
		map.put("/websocket/chat", new ChatSocketHandler(eventPublisher, events));
		SimpleUrlHandlerMapping simpleUrlHandlerMapping = new SimpleUrlHandlerMapping();
		simpleUrlHandlerMapping.setUrlMap(map);

		simpleUrlHandlerMapping.setOrder(10);
		return simpleUrlHandlerMapping;
	}

	@Bean
	public WebSocketHandlerAdapter handlerAdapter() {
		return new WebSocketHandlerAdapter();
	}


	public static void main(String[] args) {
		SpringApplication.run(ChatApplication.class, args);


	}

}
