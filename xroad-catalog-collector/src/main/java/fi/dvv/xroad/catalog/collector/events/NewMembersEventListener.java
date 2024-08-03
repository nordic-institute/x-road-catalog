package fi.dvv.xroad.catalog.collector.events;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.catalog.collector.events.NewMembersEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;

@Slf4j
@Component
public class NewMembersEventListener {
    @Autowired
    private BlockingQueue<String> fetchCompaniesQueue;
    @Autowired
    private BlockingQueue<String> fetchOrganizationsQueue;

    @EventListener
    public void handleNewMembersEvent(NewMembersEvent event) {
        log.info("New members detected: {}", event.newMembersCodes());
        fetchCompaniesQueue.addAll(event.newMembersCodes());
        log.info("{} new members sent to the FetchCompaniesTask", event.newMembersCodes().size());
        fetchOrganizationsQueue.addAll(event.newMembersCodes());
        log.info("{} new members sent to the FetchOrganizationsTask", event.newMembersCodes().size());
    }
}
