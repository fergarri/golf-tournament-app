package com.golf.tournament.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class ScorecardEventService {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long scorecardId) {
        SseEmitter emitter = new SseEmitter(0L); // sin timeout: la conexión dura mientras el cliente esté abierto
        List<SseEmitter> list = emitters.computeIfAbsent(scorecardId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        emitter.onCompletion(() -> remove(scorecardId, emitter));
        emitter.onTimeout(() -> remove(scorecardId, emitter));
        emitter.onError(e -> remove(scorecardId, emitter));

        log.debug("SSE suscripto para scorecard {}, total emitters: {}", scorecardId, list.size());
        return emitter;
    }

    public void notifyConcordanciaActualizada(Long scorecardId) {
        List<SseEmitter> list = emitters.get(scorecardId);
        if (list == null || list.isEmpty()) return;

        List<SseEmitter> dead = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name("concordanciaActualizada")
                        .data("refresh"));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        list.removeAll(dead);
        log.debug("SSE evento enviado a scorecard {}, emitters activos: {}", scorecardId, list.size());
    }

    // Heartbeat cada 25s para evitar que proxies/load-balancers cierren conexiones idle.
    // También detecta y limpia emitters muertos antes de intentar enviar un evento real.
    @Scheduled(fixedDelay = 25_000)
    public void sendHeartbeat() {
        if (emitters.isEmpty()) return;
        emitters.forEach((scorecardId, list) -> {
            if (list.isEmpty()) return;
            List<SseEmitter> dead = new CopyOnWriteArrayList<>();
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException e) {
                    dead.add(emitter);
                }
            }
            list.removeAll(dead);
        });
    }

    private void remove(Long scorecardId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(scorecardId);
        if (list != null) {
            list.remove(emitter);
        }
    }
}
