package com.turnero.api.service;

import java.time.*;
import java.util.*;

/** Converts instant bounds into indexable local timestamp intervals using the same
 * earlier-overlap/forward-gap policy as LocalDateTime.atZone on serialization. */
public final class BusinessLocalTimeRange {
    private BusinessLocalTimeRange() {}
    public record Window(LocalDateTime from, LocalDateTime to) {}
    public static List<Window> windows(Instant from, Instant to, ZoneId zone) {
        var lower = LocalDateTime.ofInstant(from, zone).minusDays(2);
        var upper = LocalDateTime.ofInstant(to, zone).plusDays(2);
        var cuts = new TreeSet<LocalDateTime>(); cuts.add(lower); cuts.add(upper);
        var rules = zone.getRules();
        var transition = rules.nextTransition(from.minus(Duration.ofDays(3)));
        while (transition != null && transition.getInstant().isBefore(to.plus(Duration.ofDays(3)))) {
            for (var cut : List.of(transition.getDateTimeBefore(), transition.getDateTimeAfter())) {
                if (cut.isAfter(lower) && cut.isBefore(upper)) cuts.add(cut);
            }
            transition = rules.nextTransition(transition.getInstant().plusNanos(1));
        }
        var points = List.copyOf(cuts); var result = new ArrayList<Window>();
        for (int index = 0; index + 1 < points.size(); index++) {
            var start = points.get(index); var end = points.get(index + 1);
            var middle = start.plusNanos(Duration.between(start, end).toNanos() / 2);
            var offsets = rules.getValidOffsets(middle);
            var offset = offsets.isEmpty() ? rules.getTransition(middle).getOffsetBefore() : offsets.getFirst();
            var instantStart = start.toInstant(offset); var instantEnd = end.toInstant(offset);
            var clippedStart = from.isAfter(instantStart) ? from : instantStart;
            var clippedEnd = to.isBefore(instantEnd) ? to : instantEnd;
            if (!clippedStart.isBefore(clippedEnd)) continue;
            var window = new Window(LocalDateTime.ofInstant(clippedStart, offset), LocalDateTime.ofInstant(clippedEnd, offset));
            if (!result.isEmpty() && result.getLast().to().equals(window.from())) {
                var previous = result.removeLast(); window = new Window(previous.from(), window.to());
            }
            result.add(window);
        }
        return List.copyOf(result);
    }
}
