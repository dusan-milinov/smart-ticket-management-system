package com.ticketmanagement.backend.seeder;

import com.ticketmanagement.backend.enums.TicketPriority;
import com.ticketmanagement.backend.enums.TicketType;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class TicketSeeder {

    static final String ADMIN = "admin@example.com";
    static final String ZIKA = "zika@example.com";
    static final String NIKOLA = "nikola@example.com";
    static final String THIRD_AGENT = "agent3@example.com";
    static final String PETAR = "petar.petrovic@example.com";
    static final String VOJIN = "vojin.vojinovic@example.com";

    static final List<Fixture> FIXTURES = List.of(
            new Fixture(1, "VPN disconnects during remote meetings",
                    "The VPN disconnects every ten minutes when joining video meetings from home.",
                    TicketType.INCIDENT, TicketPriority.HIGH, PETAR, ZIKA, null,
                    List.of(new SeedComment(ZIKA, "I traced the disconnects to an outdated VPN client."),
                            new SeedComment(PETAR, "The updated client stayed connected through my meeting."),
                            new SeedComment(ZIKA, "Connection logs confirm the tunnel is now stable.")),
                    "Updated the VPN client and verified stable connectivity during a one-hour meeting.", false),
            new Fixture(2, "Request a diagramming software license",
                    "Please provision a diagramming license for the infrastructure documentation project.",
                    TicketType.SOFTWARE_REQUEST, TicketPriority.MEDIUM, VOJIN, ZIKA, NIKOLA,
                    List.of(new SeedComment(NIKOLA, "The license is assigned and the installation link is available."),
                            new SeedComment(ADMIN, "The approved license purchase has been recorded.")),
                    "Provisioned the approved diagramming license and confirmed access with the requester.", false),
            new Fixture(3, "Shared printer leaves documents in queue",
                    "Documents sent to the second-floor shared printer remain queued without an error.",
                    TicketType.INCIDENT, TicketPriority.MEDIUM, PETAR, ZIKA, null,
                    List.of(new SeedComment(PETAR, "The same document prints successfully on the first floor.")),
                    null, false),
            new Fixture(4, "Install development tools on a new workstation",
                    "Please install the approved JDK and IDE on the new workstation for the backend project.",
                    TicketType.SOFTWARE_REQUEST, TicketPriority.LOW, VOJIN, NIKOLA, ZIKA,
                    List.of(new SeedComment(ZIKA, "I am checking the approved tool versions before installation.")),
                    null, false),
            new Fixture(5, "Office network connection repeatedly drops",
                    "The workstation loses its wired network connection several times each morning.",
                    TicketType.INCIDENT, TicketPriority.HIGH, PETAR, THIRD_AGENT, null,
                    List.of(new SeedComment(THIRD_AGENT, "I am testing a replacement cable and switch port.")),
                    null, false),
            new Fixture(6, "Request a database client installation",
                    "Please install the approved SQL client for read-only reporting against the test database.",
                    TicketType.SOFTWARE_REQUEST, TicketPriority.LOW, VOJIN, NIKOLA, null,
                    List.of(), null, false),
            new Fixture(7, "External monitor flickers after docking",
                    "The external monitor flickers when the laptop is connected to the office docking station.",
                    TicketType.INCIDENT, TicketPriority.MEDIUM, PETAR, ZIKA, null,
                    List.of(), null, false),
            new Fixture(8, "Request a screen recording application",
                    "A screen recorder was requested for training, but the team will use the existing meeting tool.",
                    TicketType.SOFTWARE_REQUEST, TicketPriority.LOW, VOJIN, THIRD_AGENT, null,
                    List.of(), null, true)
    );

    private final TicketFixtureSeeder fixtureSeeder;

    public void validateTypes() {
        for (String name : List.of("INCIDENT", "SOFTWARE_REQUEST")) {
            try {
                TicketType.valueOf(name);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Required seed ticket type is unavailable: " + name, exception);
            }
        }
    }

    /** Seeds each missing aggregate independently. A failure stops further fixtures. */
    public void seed() {
        for (Fixture fixture : FIXTURES) {
            fixtureSeeder.seed(fixture);
        }
    }

    record SeedComment(String authorEmail, String content) {
    }

    record Fixture(int number, String title, String description, TicketType type, TicketPriority priority,
                   String creatorEmail, String initialAgentEmail, String reassignedAgentEmail,
                   List<SeedComment> comments, String resolution, boolean cancelled) {

        String ticketNumber() {
            return "TKT-SEED-%04d".formatted(number);
        }
    }
}
