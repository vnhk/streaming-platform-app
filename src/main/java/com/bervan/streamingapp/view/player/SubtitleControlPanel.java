package com.bervan.streamingapp.view.player;

import com.bervan.common.component.BervanButton;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Component for controlling subtitle delays with optional permanent save per language.
 */
public class SubtitleControlPanel extends VerticalLayout {
    private static final double SUBTITLE_DELAY_STEP = 0.5;

    private final NumberField enDelayField;
    private final NumberField plDelayField;
    private final NumberField esDelayField;

    private final Button enSaveButton;
    private final Button plSaveButton;
    private final Button esSaveButton;

    public SubtitleControlPanel(double initialEnDelay, double initialPlDelay, double initialEsDelay) {
        setAlignItems(Alignment.START);
        setSpacing(true);

        enDelayField = createDelayField("Subtitle Delay (EN) [s]", initialEnDelay);
        plDelayField = createDelayField("Subtitle Delay (PL) [s]", initialPlDelay);
        esDelayField = createDelayField("Subtitle Delay (ES) [s]", initialEsDelay);

        enSaveButton = createSaveButton("Save EN permanently");
        plSaveButton = createSaveButton("Save PL permanently");
        esSaveButton = createSaveButton("Save ES permanently");

        Button resetButton = new BervanButton("Reset Delays",
                e -> resetDelays(initialEnDelay, initialPlDelay, initialEsDelay));

        add(
                buildRow(enDelayField, enSaveButton),
                buildRow(plDelayField, plSaveButton),
                buildRow(esDelayField, esSaveButton),
                resetButton
        );
    }

    private Button createSaveButton(String label) {
        Button btn = new BervanButton(label, e -> {});
        btn.setVisible(false);
        return btn;
    }

    private HorizontalLayout buildRow(NumberField field, Button saveButton) {
        HorizontalLayout row = new HorizontalLayout(field, saveButton);
        row.setAlignItems(Alignment.END);
        row.setSpacing(true);
        return row;
    }

    private NumberField createDelayField(String label, double initialValue) {
        NumberField field = new NumberField(label);
        field.setStep(SUBTITLE_DELAY_STEP);
        field.setWidth("260px");
        field.setValue(initialValue);
        return field;
    }

    private void resetDelays(double enDelay, double plDelay, double esDelay) {
        enDelayField.setValue(enDelay);
        plDelayField.setValue(plDelay);
        esDelayField.setValue(esDelay);
    }

    public void setEnDelayChangeListener(Consumer<Double> listener) {
        enDelayField.addValueChangeListener(e ->
                listener.accept(Optional.ofNullable(e.getValue()).orElse(0.0)));
    }

    public void setPlDelayChangeListener(Consumer<Double> listener) {
        plDelayField.addValueChangeListener(e ->
                listener.accept(Optional.ofNullable(e.getValue()).orElse(0.0)));
    }

    public void setEsDelayChangeListener(Consumer<Double> listener) {
        esDelayField.addValueChangeListener(e ->
                listener.accept(Optional.ofNullable(e.getValue()).orElse(0.0)));
    }

    public void setEnSavePermanentlyListener(Runnable listener) {
        enSaveButton.setVisible(true);
        enSaveButton.addClickListener(e -> listener.run());
    }

    public void setPlSavePermanentlyListener(Runnable listener) {
        plSaveButton.setVisible(true);
        plSaveButton.addClickListener(e -> listener.run());
    }

    public void setEsSavePermanentlyListener(Runnable listener) {
        esSaveButton.setVisible(true);
        esSaveButton.addClickListener(e -> listener.run());
    }

    public double getEnDelay() {
        return Optional.ofNullable(enDelayField.getValue()).orElse(0.0);
    }

    public double getPlDelay() {
        return Optional.ofNullable(plDelayField.getValue()).orElse(0.0);
    }

    public double getEsDelay() {
        return Optional.ofNullable(esDelayField.getValue()).orElse(0.0);
    }
}
