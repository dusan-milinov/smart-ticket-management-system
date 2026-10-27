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
    static final String ANDJELA = "andjela.petrovic@example.com";
    static final String EMILY = "emily.wilson@example.com";
    static final String VESNA = "vesna.jovanovic@example.com";
    static final String SARAH = "sarah.parker@example.com";

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
                    List.of(), null, true),
            new Fixture(9, "Payroll portal fails after sign-in",
                    "The payroll portal shows a blank page after sign-in, preventing the monthly export.",
                    TicketType.INCIDENT, TicketPriority.HIGH, VESNA, ZIKA, ANDJELA,
                    List.of(new SeedComment(ANDJELA, "The browser is retaining an expired portal session."),
                            new SeedComment(VESNA, "After clearing the session I can export the payroll report."),
                            new SeedComment(ADMIN, "The payroll team has confirmed access is restored.")),
                    "Cleared the expired session and verified sign-in and payroll export with the requester.", false),
            new Fixture(10, "Install an approved PDF editor",
                    "Please install the licensed PDF editor so I can annotate supplier contract drafts.",
                    TicketType.SOFTWARE_REQUEST, TicketPriority.MEDIUM, SARAH, EMILY, null,
                    List.of(new SeedComment(EMILY, "The license is available; installation is scheduled for this afternoon.")),
                    null, false),
            new Fixture(11, "Laptop camera is unavailable in meetings",
                    "The built-in camera is not detected by either meeting application after the laptop update.",
                    TicketType.INCIDENT, TicketPriority.MEDIUM, PETAR, ANDJELA, null,
                    List.of(), null, false),
            new Fixture(12, "Enable the spreadsheet reporting add-in",
                    "Please enable the approved reporting add-in for the weekly warehouse inventory workbook.",
                    TicketType.SOFTWARE_REQUEST, TicketPriority.LOW, VOJIN, NIKOLA, EMILY,
                    List.of(new SeedComment(EMILY, "The signed add-in is installed and connected to the reporting service."),
                            new SeedComment(VOJIN, "The inventory workbook refreshes successfully now.")),
                    "Installed the signed reporting add-in and verified a complete inventory refresh.", false),
            new Fixture(13, "Shared drive rejects project uploads",
                    "Uploading project documents to the team drive fails with an access-denied message.",
                    TicketType.INCIDENT, TicketPriority.HIGH, SARAH, THIRD_AGENT, null,
                    List.of(new SeedComment(SARAH, "Reading existing files works, but uploading a small text file also fails."),
                            new SeedComment(THIRD_AGENT, "I am comparing the folder permissions with the team access group.")),
                    null, false),
            new Fixture(14, "Request a desktop note-taking application",
                    "Please install the desktop notes application for workshop preparation; the workshop was later postponed.",
                    TicketType.SOFTWARE_REQUEST, TicketPriority.LOW, VESNA, ANDJELA, null,
                    List.of(new SeedComment(VESNA, "Please cancel this request because the workshop is no longer scheduled.")),
                    null, true),
            new Fixture(15, "Meeting room display shows no signal",
                    "The third-floor meeting room display shows no signal when connected through the desk adapter.",
                    TicketType.INCIDENT, TicketPriority.MEDIUM, VOJIN, ZIKA, null,
                    List.of(new SeedComment(ZIKA, "A replacement adapter restores the picture on both test laptops.")),
                    "Replaced the faulty display adapter and verified video output from two laptops.", false),
            new Fixture(16, "Install the approved API testing client",
                    "I need the approved API testing client to validate the staging integration endpoints.",
                    TicketType.SOFTWARE_REQUEST, TicketPriority.MEDIUM, PETAR, EMILY, NIKOLA,
                    List.of(new SeedComment(ADMIN, "The request is approved under the existing engineering software allocation."),
                            new SeedComment(NIKOLA, "I am preparing the installation package and staging environment settings.")),
                    null, false),
            new Fixture(17, "Email search omits recent messages",
                    "Desktop email search does not return messages received this week, although they are visible in the inbox.",
                    TicketType.INCIDENT, TicketPriority.LOW, VESNA, EMILY, null,
                    List.of(), null, false),
            new Fixture(18, "Provision a project planning license",
                    "Please provision the approved planning application for coordinating the office relocation.",
                    TicketType.SOFTWARE_REQUEST, TicketPriority.HIGH, SARAH, ANDJELA, null,
                    List.of(new SeedComment(ANDJELA, "The license is assigned and the relocation workspace is ready."),
                            new SeedComment(SARAH, "I can open the workspace and invite the project team.")),
                    "Assigned the planning license and verified workspace access and team invitations.", false),
            new Fixture(19, "Duplicate alert for an offline printer",
                    "The reception printer appears offline; facilities confirmed that this outage is already tracked in their ticket.",
                    TicketType.INCIDENT, TicketPriority.LOW, PETAR, THIRD_AGENT, ZIKA,
                    List.of(new SeedComment(ZIKA, "Facilities is replacing the printer power supply under the original request."),
                            new SeedComment(ADMIN, "Cancelling this duplicate so updates remain on the facilities request.")),
                    null, true),
            new Fixture(20, "Request an accessibility reading tool",
                    "Please install the approved text-to-speech application on my workstation for reviewing long documents.",
                    TicketType.SOFTWARE_REQUEST, TicketPriority.MEDIUM, VESNA, ANDJELA, null,
                    List.of(), null, false),
            new Fixture(21, "Remote desktop freezes during report runs",
                    "The remote desktop session becomes unresponsive when the daily sales report is generated.",
                    TicketType.INCIDENT, TicketPriority.HIGH, VOJIN, NIKOLA, THIRD_AGENT,
                    List.of(new SeedComment(THIRD_AGENT, "The session host has a stalled print-redirection process."),
                            new SeedComment(VOJIN, "The report completed after reconnecting to the repaired host."),
                            new SeedComment(THIRD_AGENT, "Monitoring shows normal resource usage during the next report run.")),
                    "Restarted the stalled redirection service, applied its patch, and verified two report runs without freezing.", false),
            new Fixture(22, "Install the approved image editing application",
                    "Please install the image editor for preparing product screenshots for the internal knowledge base.",
                    TicketType.SOFTWARE_REQUEST, TicketPriority.LOW, SARAH, ZIKA, null,
                    List.of(new SeedComment(SARAH, "The standard edition has all the features needed for this work.")),
                    null, false),
            new Fixture(23, "Headset microphone records distorted audio",
                    "The USB headset microphone produces distorted audio in calls and in a local recording test.",
                    TicketType.INCIDENT, TicketPriority.MEDIUM, PETAR, EMILY, null,
                    List.of(new SeedComment(EMILY, "The microphone gain was being applied twice by the audio driver."),
                            new SeedComment(PETAR, "The test recording and my latest call both sound clear.")),
                    "Disabled duplicate microphone gain processing and confirmed clear recordings and call audio.", false),
            new Fixture(24, "Request a separate file transfer client",
                    "A file transfer client was requested for supplier deliveries, but the supplier now uses our approved web portal.",
                    TicketType.SOFTWARE_REQUEST, TicketPriority.LOW, VOJIN, NIKOLA, null,
                    List.of(), null, true),
            new Fixture(25, "Wireless connection fails in the training room",
                    "My laptop connects to office wireless elsewhere but cannot obtain an address in the training room.",
                    TicketType.INCIDENT, TicketPriority.HIGH, VESNA, ANDJELA, THIRD_AGENT,
                    List.of(new SeedComment(THIRD_AGENT, "I am checking address allocation on the training room access point.")),
                    null, false),
            new Fixture(26, "Enable the approved browser translation extension",
                    "Please deploy the approved translation extension for reviewing supplier documentation in the browser.",
                    TicketType.SOFTWARE_REQUEST, TicketPriority.LOW, SARAH, EMILY, null,
                    List.of(new SeedComment(EMILY, "The approved extension is deployed through the managed browser policy.")),
                    "Deployed the managed translation extension and verified it works on supplier documentation.", false),
            new Fixture(27, "Calendar invitations display the wrong time",
                    "Desktop calendar invitations are displayed one hour later than the same invitations in webmail.",
                    TicketType.INCIDENT, TicketPriority.MEDIUM, VOJIN, ZIKA, null,
                    List.of(), null, false),
            new Fixture(28, "Install the database migration review tool",
                    "Please install the approved schema comparison tool to review migration scripts before the release.",
                    TicketType.SOFTWARE_REQUEST, TicketPriority.HIGH, PETAR, THIRD_AGENT, ANDJELA,
                    List.of(new SeedComment(ADMIN, "The tool is approved for the staging database only."),
                            new SeedComment(ANDJELA, "The installation and read-only staging connection are ready."),
                            new SeedComment(PETAR, "The schema comparison completed and exported the review report.")),
                    "Installed the schema comparison tool with read-only staging access and verified report export.", false)
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
