package com.bervan.streamingapp.view;

import com.bervan.streamingapp.config.ProductionData;
import com.bervan.streamingapp.config.ProductionDetails;
import com.bervan.streamingapp.view.player.AbstractProductionPlayerView;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractRemoteControlView extends AbstractStreamingPage implements BeforeEnterObserver {
    public static final String ROUTE_NAME = "/streaming-platform/remote-control";

    private String roomId;
    private Div connectionStatus;
    private Span connectionText;
    private final Map<String, ProductionData> streamingProductionData;

    public AbstractRemoteControlView(Map<String, ProductionData> streamingProductionData) {
        super(ROUTE_NAME, AbstractProductionPlayerView.ROUTE_NAME, AbstractProductionDetailsView.ROUTE_NAME);
        this.streamingProductionData = streamingProductionData;
        addClassName("remote-control-view");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        roomId = event.getLocation().getQueryParameters()
                .getSingleParameter("roomId").orElse(null);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        createUI();
        if (roomId != null && !roomId.isEmpty()) {
            addRemoteControlScript();
        }
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        getElement().executeJs("""
            if (window.remoteControl) {
                if (window.remoteControl.ws) {
                    window.remoteControl.ws.onclose = null;
                    window.remoteControl.ws.close();
                }
                window.remoteControl = null;
            }
        """);
    }

    private void createUI() {
        removeAll();

        // Main container
        VerticalLayout mainContainer = new VerticalLayout();
        mainContainer.addClassName("remote-main-container");
        mainContainer.setSizeFull();
        mainContainer.setPadding(false);
        mainContainer.setSpacing(false);

        // Header with connection status
        mainContainer.add(createHeader());

        // Room ID section (if not connected)
        if (roomId == null || roomId.isEmpty()) {
            mainContainer.add(createRoomIdSection());
        }

        // Quick Navigation
        mainContainer.add(createQuickNavSection());

        // Main Controls
        mainContainer.add(createControlsSection());

        // Search & Play Section
        mainContainer.add(createSearchSection());

        add(mainContainer);
        addStyles();
    }

    private Component createHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.addClassName("remote-header");
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setPadding(true);

        // Title
        H2 title = new H2("TV Remote");
        title.addClassName("remote-title");

        // Connection status
        connectionStatus = new Div();
        connectionStatus.addClassName("connection-status");
        connectionStatus.addClassName("disconnected");

        Span dot = new Span();
        dot.addClassName("status-dot");

        connectionText = new Span(roomId != null ? "Room: " + roomId : "Not connected");
        connectionText.addClassName("status-text");

        connectionStatus.add(dot, connectionText);

        header.add(title, connectionStatus);
        return header;
    }

    private Component createRoomIdSection() {
        VerticalLayout section = new VerticalLayout();
        section.addClassName("room-section");
        section.setAlignItems(FlexComponent.Alignment.CENTER);
        section.setPadding(true);

        Paragraph info = new Paragraph("Enter the Room ID shown on your TV to connect");
        info.addClassName("room-info");

        TextField roomInput = new TextField();
        roomInput.setPlaceholder("Enter Room ID (e.g., room_12345)");
        roomInput.addClassName("room-input");
        roomInput.setWidthFull();

        Button connectBtn = new Button("Connect", new Icon(VaadinIcon.CONNECT));
        connectBtn.addClassName("connect-btn");
        connectBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        connectBtn.setWidthFull();
        connectBtn.addClickListener(e -> {
            String inputRoom = roomInput.getValue();
            if (inputRoom != null && !inputRoom.trim().isEmpty()) {
                roomId = inputRoom.trim();
                connectionText.setText("Room: " + roomId);
                addRemoteControlScript();
                section.setVisible(false);
            }
        });

        section.add(info, roomInput, connectBtn);
        return section;
    }

    private Component createQuickNavSection() {
        VerticalLayout section = new VerticalLayout();
        section.addClassName("nav-section");
        section.setPadding(true);
        section.setSpacing(true);

        H4 sectionTitle = new H4("Quick Navigation");
        sectionTitle.addClassName("section-title");

        HorizontalLayout navButtons = new HorizontalLayout();
        navButtons.addClassName("nav-buttons");
        navButtons.setWidthFull();
        navButtons.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        // Streaming Home
        Button homeBtn = createNavButton("Home", VaadinIcon.HOME, AbstractProductionListView.ROUTE_NAME);

        // Search (opens search dialog)
        Button searchBtn = new Button(new Icon(VaadinIcon.SEARCH));
        searchBtn.addClassName("nav-btn");
        searchBtn.setText("Search");
        searchBtn.addClickListener(e -> openSearchDialog());

        // Recently Watched (if available)
        Button recentBtn = createNavButton("Recent", VaadinIcon.CLOCK, AbstractProductionListView.ROUTE_NAME + "?tab=recent");

        navButtons.add(homeBtn, searchBtn, recentBtn);
        section.add(sectionTitle, navButtons);
        return section;
    }

    private Button createNavButton(String text, VaadinIcon icon, String url) {
        Button btn = new Button(text, new Icon(icon));
        btn.addClassName("nav-btn");
        btn.addClickListener(e -> sendNavigate(url));
        return btn;
    }

    private Component createControlsSection() {
        VerticalLayout section = new VerticalLayout();
        section.addClassName("controls-section");
        section.setPadding(true);
        section.setSpacing(true);
        section.setAlignItems(FlexComponent.Alignment.CENTER);

        // Playback Controls (large center area)
        HorizontalLayout playbackRow = new HorizontalLayout();
        playbackRow.addClassName("playback-row");
        playbackRow.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        playbackRow.setAlignItems(FlexComponent.Alignment.CENTER);

        Button seekBackBtn = createControlButton(VaadinIcon.BACKWARDS, "seek-btn", "-10s");
        seekBackBtn.addClickListener(e -> sendCommand("SEEK", "{relative: -10}"));

        Button playPauseBtn = createControlButton(VaadinIcon.PLAY, "play-btn", "");
        playPauseBtn.addClassName("play-pause-btn");
        playPauseBtn.addClickListener(e -> sendCommand("TOGGLE_PLAY", "null"));

        Button seekFwdBtn = createControlButton(VaadinIcon.FORWARD, "seek-btn", "+10s");
        seekFwdBtn.addClickListener(e -> sendCommand("SEEK", "{relative: 10}"));

        playbackRow.add(seekBackBtn, playPauseBtn, seekFwdBtn);

        // Volume Row
        HorizontalLayout volumeRow = new HorizontalLayout();
        volumeRow.addClassName("volume-row");
        volumeRow.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        volumeRow.setAlignItems(FlexComponent.Alignment.CENTER);

        Button volDownBtn = createControlButton(VaadinIcon.VOLUME_DOWN, "vol-btn", "");
        volDownBtn.addClickListener(e -> sendCommand("VOLUME", "{relative: -0.1}"));

        Span volLabel = new Span("Volume");
        volLabel.addClassName("vol-label");

        Button volUpBtn = createControlButton(VaadinIcon.VOLUME_UP, "vol-btn", "");
        volUpBtn.addClickListener(e -> sendCommand("VOLUME", "{relative: 0.1}"));

        volumeRow.add(volDownBtn, volLabel, volUpBtn);

        // Display Controls
        HorizontalLayout displayRow = new HorizontalLayout();
        displayRow.addClassName("display-row");
        displayRow.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        Button maximizeBtn = createSmallButton("Maximize", VaadinIcon.EXPAND_SQUARE);
        maximizeBtn.addClickListener(e -> sendCommand("MAXIMIZE", "null"));

        Button pipBtn = createSmallButton("PiP", VaadinIcon.VIEWPORT);
        pipBtn.addClickListener(e -> sendCommand("PIP", "null"));

        Button fullscreenBtn = createSmallButton("Fullscreen", VaadinIcon.EXPAND_FULL);
        fullscreenBtn.addClickListener(e -> sendCommand("FULLSCREEN_PROMPT", "null"));

        displayRow.add(maximizeBtn, pipBtn, fullscreenBtn);

        section.add(playbackRow, volumeRow, displayRow);
        return section;
    }

    private Button createControlButton(VaadinIcon icon, String className, String label) {
        Button btn = new Button(new Icon(icon));
        btn.addClassName("control-btn");
        btn.addClassName(className);
        if (!label.isEmpty()) {
            Span labelSpan = new Span(label);
            labelSpan.addClassName("btn-label");
            btn.getElement().appendChild(labelSpan.getElement());
        }
        return btn;
    }

    private Button createSmallButton(String text, VaadinIcon icon) {
        Button btn = new Button(text, new Icon(icon));
        btn.addClassName("small-btn");
        return btn;
    }

    private Component createSearchSection() {
        VerticalLayout section = new VerticalLayout();
        section.addClassName("search-section");
        section.setPadding(true);

        H4 sectionTitle = new H4("Search & Play");
        sectionTitle.addClassName("section-title");

        Button openSearchBtn = new Button("Find something to watch", new Icon(VaadinIcon.SEARCH));
        openSearchBtn.addClassName("search-open-btn");
        openSearchBtn.addThemeVariants(ButtonVariant.LUMO_LARGE);
        openSearchBtn.setWidthFull();
        openSearchBtn.addClickListener(e -> openSearchDialog());

        section.add(sectionTitle, openSearchBtn);
        return section;
    }

    private void openSearchDialog() {
        Dialog dialog = new Dialog();
        dialog.addClassName("search-dialog");
        dialog.setWidth("95vw");
        dialog.setMaxWidth("500px");
        dialog.setHeight("80vh");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        content.setSizeFull();

        // Search input
        TextField searchInput = new TextField();
        searchInput.setPlaceholder("Search movies, series...");
        searchInput.setWidthFull();
        searchInput.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchInput.setValueChangeMode(ValueChangeMode.LAZY);
        searchInput.setValueChangeTimeout(300);
        searchInput.addClassName("dialog-search-input");

        // Results container
        VerticalLayout results = new VerticalLayout();
        results.addClassName("search-results");
        results.setPadding(false);
        results.setSpacing(false);

        // Load all productions initially (recent/popular)
        List<ProductionData> allProductions = streamingProductionData.values().stream()
                .filter(p -> p.getProductionDetails() != null)
                .sorted(Comparator.comparing(p -> p.getProductionDetails().getName() != null ?
                        p.getProductionDetails().getName() : ""))
                .limit(20)
                .collect(Collectors.toList());

        updateSearchResults(results, allProductions);

        // Search listener
        searchInput.addValueChangeListener(e -> {
            String query = e.getValue().toLowerCase().trim();
            List<ProductionData> filtered;

            if (query.isEmpty()) {
                filtered = allProductions;
            } else {
                filtered = streamingProductionData.values().stream()
                        .filter(p -> p.getProductionDetails() != null)
                        .filter(p -> {
                            ProductionDetails d = p.getProductionDetails();
                            return (d.getName() != null && d.getName().toLowerCase().contains(query)) ||
                                    (d.getDescription() != null && d.getDescription().toLowerCase().contains(query));
                        })
                        .limit(20)
                        .collect(Collectors.toList());
            }

            updateSearchResults(results, filtered);
        });

        // Close button
        Button closeBtn = new Button(new Icon(VaadinIcon.CLOSE));
        closeBtn.addClassName("dialog-close-btn");
        closeBtn.addClickListener(e -> dialog.close());

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.add(new H3("Search"), closeBtn);

        content.add(header, searchInput, results);
        content.setFlexGrow(1, results);

        dialog.add(content);
        dialog.open();
    }

    private void updateSearchResults(VerticalLayout results, List<ProductionData> productions) {
        results.removeAll();

        if (productions.isEmpty()) {
            Paragraph noResults = new Paragraph("No results found");
            noResults.addClassName("no-results");
            results.add(noResults);
            return;
        }

        for (ProductionData prod : productions) {
            results.add(createProductionItem(prod));
        }
    }

    private Component createProductionItem(ProductionData prod) {
        HorizontalLayout item = new HorizontalLayout();
        item.addClassName("production-item");
        item.setWidthFull();
        item.setAlignItems(FlexComponent.Alignment.CENTER);

        ProductionDetails details = prod.getProductionDetails();

        // Info
        VerticalLayout info = new VerticalLayout();
        info.setPadding(false);
        info.setSpacing(false);

        Span title = new Span(details.getName() != null ? details.getName() : "Unknown");
        title.addClassName("prod-title");

        Span meta = new Span();
        meta.addClassName("prod-meta");
        StringBuilder metaText = new StringBuilder();
        if (details.getType() != null) {
            metaText.append(details.getType().name());
        }
        if (details.getReleaseYearStart() != null) {
            if (metaText.length() > 0) metaText.append(" • ");
            metaText.append(details.getReleaseYearStart());
        }
        meta.setText(metaText.toString());

        info.add(title, meta);

        // Play button
        Button playBtn = new Button(new Icon(VaadinIcon.PLAY));
        playBtn.addClassName("prod-play-btn");
        playBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        playBtn.addClickListener(e -> {
            // Navigate to details page where user can select episode/video
            String url = AbstractProductionDetailsView.ROUTE_NAME + "/" + prod.getMainFolder().getId();
            sendNavigate(url);
        });

        item.add(info, playBtn);
        item.setFlexGrow(1, info);

        return item;
    }

    private void sendCommand(String action, String data) {
        String script = "if(window.remoteControl) window.remoteControl.sendCommand('" + action + "', 'video', " + data + ")";
        getElement().executeJs(script);
    }

    private void sendNavigate(String url) {
        String script = "if(window.remoteControl) window.remoteControl.sendCommand('NAVIGATE', 'page', {url: '" + url + "'})";
        getElement().executeJs(script);
    }

    private void addRemoteControlScript() {
        getElement().executeJs("""
            if (window.remoteControl) {
                window.remoteControl.ws.onclose = null;
                window.remoteControl.ws.close();
            }

            class MobileRemoteControl {
                constructor(roomId) {
                    this.roomId = roomId;
                    this.connect();
                }

                connect() {
                    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
                    const wsUrl = `${protocol}//${window.location.host}/ws/remote-control?deviceType=REMOTE&roomId=${this.roomId}`;

                    this.ws = new WebSocket(wsUrl);

                    this.ws.onopen = () => {
                        console.log('Remote connected to room:', this.roomId);
                        this.updateStatus(true);
                    };

                    this.ws.onclose = () => {
                        console.log('Remote disconnected');
                        this.updateStatus(false);
                        setTimeout(() => this.connect(), 3000);
                    };

                    this.ws.onerror = (error) => {
                        console.error('WebSocket error:', error);
                        this.updateStatus(false);
                    };

                    this.ws.onmessage = (event) => {
                        try {
                            const msg = JSON.parse(event.data);
                            if (msg.type === 'STATUS') {
                                this.showFeedback(msg.message);
                            }
                        } catch(e) {}
                    };
                }

                updateStatus(connected) {
                    const statusEl = document.querySelector('.connection-status');
                    if (statusEl) {
                        statusEl.classList.toggle('connected', connected);
                        statusEl.classList.toggle('disconnected', !connected);
                    }
                }

                sendCommand(action, target, data) {
                    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                        const message = {
                            action: action,
                            target: target,
                            data: data,
                            roomId: this.roomId
                        };
                        this.ws.send(JSON.stringify(message));
                        this.showFeedback(action);
                    } else {
                        this.showFeedback('Not connected', true);
                    }
                }

                showFeedback(action, isError) {
                    // Brief visual feedback
                    let feedback = document.getElementById('cmd-feedback');
                    if (!feedback) {
                        feedback = document.createElement('div');
                        feedback.id = 'cmd-feedback';
                        feedback.style.cssText = `
                            position: fixed; bottom: 20px; left: 50%;
                            transform: translateX(-50%);
                            padding: 12px 24px; border-radius: 25px;
                            background: var(--glass-bg, rgba(0,0,0,0.8));
                            color: white; font-weight: 500;
                            z-index: 9999; opacity: 0;
                            transition: opacity 0.2s ease;
                        `;
                        document.body.appendChild(feedback);
                    }

                    feedback.textContent = action;
                    feedback.style.background = isError ? '#f44336' : 'var(--glass-bg, rgba(0,0,0,0.8))';
                    feedback.style.opacity = '1';

                    setTimeout(() => { feedback.style.opacity = '0'; }, 1000);
                }
            }

            window.remoteControl = new MobileRemoteControl($0);
        """, roomId);
    }

    private void addStyles() {
        getElement().executeJs("""
            const style = document.createElement('style');
            style.textContent = `
                .remote-control-view {
                    background: var(--lumo-base-color);
                    min-height: 100vh;
                }

                .remote-main-container {
                    max-width: 500px;
                    margin: 0 auto;
                }

                .remote-header {
                    background: var(--glass-bg, rgba(17, 25, 40, 0.9));
                    border-bottom: 1px solid var(--glass-border, rgba(255,255,255,0.1));
                }

                .remote-title {
                    margin: 0;
                    font-size: 1.25rem;
                    color: var(--glass-text-primary, white);
                }

                .connection-status {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    padding: 6px 12px;
                    border-radius: 20px;
                    background: var(--glass-btn-bg, rgba(255,255,255,0.1));
                }

                .status-dot {
                    width: 8px;
                    height: 8px;
                    border-radius: 50%;
                    background: #f44336;
                }

                .connection-status.connected .status-dot {
                    background: #4caf50;
                    box-shadow: 0 0 8px #4caf50;
                }

                .status-text {
                    font-size: 0.8rem;
                    color: var(--glass-text-secondary, rgba(255,255,255,0.7));
                }

                .room-section {
                    background: var(--glass-bg, rgba(17, 25, 40, 0.5));
                    border-radius: 16px;
                    margin: 16px;
                }

                .room-info {
                    color: var(--glass-text-secondary, rgba(255,255,255,0.7));
                    text-align: center;
                }

                .room-input {
                    --vaadin-input-field-border-radius: 12px;
                }

                .connect-btn {
                    margin-top: 8px;
                }

                .section-title {
                    color: var(--glass-text-secondary, rgba(255,255,255,0.6));
                    font-size: 0.85rem;
                    text-transform: uppercase;
                    letter-spacing: 0.05em;
                    margin: 0 0 12px 0;
                }

                .nav-section {
                    border-bottom: 1px solid var(--glass-border, rgba(255,255,255,0.08));
                }

                .nav-btn {
                    flex: 1;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    gap: 8px;
                    padding: 16px 8px;
                    background: var(--glass-btn-bg, rgba(255,255,255,0.05));
                    border: 1px solid var(--glass-border, rgba(255,255,255,0.1));
                    border-radius: 12px;
                    color: var(--glass-text-primary, white);
                    font-size: 0.8rem;
                }

                .nav-btn:hover {
                    background: var(--glass-btn-hover-bg, rgba(255,255,255,0.1));
                }

                .controls-section {
                    padding: 24px 16px;
                }

                .playback-row {
                    gap: 24px;
                    margin-bottom: 24px;
                }

                .control-btn {
                    width: 64px;
                    height: 64px;
                    border-radius: 50%;
                    background: var(--glass-btn-bg, rgba(255,255,255,0.1));
                    border: 1px solid var(--glass-border, rgba(255,255,255,0.15));
                    color: var(--glass-text-primary, white);
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    transition: all 0.2s ease;
                }

                .control-btn:hover, .control-btn:active {
                    background: var(--glass-btn-hover-bg, rgba(255,255,255,0.2));
                    transform: scale(1.05);
                }

                .play-pause-btn {
                    width: 80px;
                    height: 80px;
                    background: var(--glass-primary, var(--lumo-primary-color));
                    border-color: var(--glass-primary, var(--lumo-primary-color));
                }

                .play-pause-btn:hover {
                    background: var(--glass-primary-hover, var(--lumo-primary-color));
                    box-shadow: 0 0 20px var(--glass-primary, var(--lumo-primary-color));
                }

                .btn-label {
                    font-size: 0.65rem;
                    margin-top: 4px;
                    opacity: 0.8;
                }

                .volume-row {
                    gap: 16px;
                    margin-bottom: 24px;
                }

                .vol-btn {
                    width: 56px;
                    height: 56px;
                }

                .vol-label {
                    color: var(--glass-text-secondary, rgba(255,255,255,0.6));
                    font-size: 0.85rem;
                }

                .display-row {
                    gap: 12px;
                }

                .small-btn {
                    padding: 10px 16px;
                    background: var(--glass-btn-bg, rgba(255,255,255,0.05));
                    border: 1px solid var(--glass-border, rgba(255,255,255,0.1));
                    border-radius: 8px;
                    color: var(--glass-text-primary, white);
                    font-size: 0.8rem;
                }

                .small-btn:hover {
                    background: var(--glass-btn-hover-bg, rgba(255,255,255,0.1));
                }

                .search-section {
                    margin: 16px;
                    padding: 16px;
                    background: var(--glass-bg, rgba(17, 25, 40, 0.5));
                    border-radius: 16px;
                }

                .search-open-btn {
                    background: var(--glass-primary-bg, rgba(99, 102, 241, 0.2));
                    border: 1px solid var(--glass-primary, var(--lumo-primary-color));
                }

                .search-dialog {
                    --lumo-dialog-overlay-background: var(--glass-bg, rgba(17, 25, 40, 0.98));
                }

                .dialog-close-btn {
                    background: transparent;
                    color: var(--glass-text-secondary);
                }

                .search-results {
                    overflow-y: auto;
                }

                .production-item {
                    padding: 12px;
                    border-bottom: 1px solid var(--glass-border, rgba(255,255,255,0.08));
                    cursor: pointer;
                    transition: background 0.2s ease;
                }

                .production-item:hover {
                    background: var(--glass-btn-hover-bg, rgba(255,255,255,0.05));
                }

                .prod-title {
                    font-weight: 500;
                    color: var(--glass-text-primary, white);
                }

                .prod-meta {
                    font-size: 0.8rem;
                    color: var(--glass-text-secondary, rgba(255,255,255,0.6));
                }

                .prod-play-btn {
                    min-width: 44px;
                    height: 44px;
                    border-radius: 50%;
                }

                .no-results {
                    text-align: center;
                    color: var(--glass-text-secondary, rgba(255,255,255,0.5));
                    padding: 32px;
                }
            `;
            document.head.appendChild(style);
        """);
    }
}
