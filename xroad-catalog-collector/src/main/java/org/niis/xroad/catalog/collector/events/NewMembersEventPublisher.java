package org.niis.xroad.catalog.collector.events;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NewMembersEventPublisher {
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    public void publishNewMembersEvent(List<String> newMembersCodes) {
        NewMembersEvent newMembersEvent = new NewMembersEvent(newMembersCodes);
        applicationEventPublisher.publishEvent(newMembersEvent);
    }
}
