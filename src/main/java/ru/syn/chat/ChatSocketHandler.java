package ru.syn.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.UnicastProcessor;
import ru.syn.chat.entity.Event;
import ru.syn.chat.entity.EventBuilder;

import java.util.Optional;

public class ChatSocketHandler implements WebSocketHandler {

    private Sinks.Many<Event> eventPublisher;
    private Flux<String> outputEvents;
    private ObjectMapper mapper;

    public ChatSocketHandler(Sinks.Many<Event> eventPublisher, Flux<Event> events) {
        this.eventPublisher = eventPublisher;
        this.mapper = new ObjectMapper();
        this.outputEvents = Flux.from(events).map(this::toJSON);
    }
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        WebSocketMessageSubscriber subscriber = new WebSocketMessageSubscriber(eventPublisher);
        return session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .map(this::toEvent)
                .doOnNext(subscriber::onNext)
                .doOnError(subscriber::onError)
                .doOnComplete(subscriber::onComplete)
                .zipWith(session.send(outputEvents.map(session::textMessage)))
                .then();
    }

    private Event toEvent(String json) {
        try {
            return mapper.readValue(json, Event.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Invalid JSON: " + json, e);
        }
    }

    private String toJSON(Event event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static class WebSocketMessageSubscriber {
        private Sinks.Many<Event> eventPublisher;
        private Optional<Event> lastReceivedEvent = Optional.empty();

        public WebSocketMessageSubscriber(Sinks.Many<Event> eventPublisher) {
            this.eventPublisher = eventPublisher;
        }

        public void onNext(Event event) {
            lastReceivedEvent = Optional.of(event);
            eventPublisher.emitNext(event, Sinks.EmitFailureHandler.FAIL_FAST);
        }

        public void onError(Throwable err) {
            err.printStackTrace();
        }

        public void onComplete() {
            lastReceivedEvent.ifPresent(event ->  eventPublisher.emitNext(
                    new EventBuilder().type(Event.Type.USER_LEFT)
                            .withPayload()
                            .user(event.getUser())
                            .build(), Sinks.EmitFailureHandler.FAIL_FAST
            ));
        }
    }
}
