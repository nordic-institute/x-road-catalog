package org.niis.xroad.catalog.collector.events;

import java.util.List;

public record NewMembersEvent(List<String> newMembersCodes) {
}
