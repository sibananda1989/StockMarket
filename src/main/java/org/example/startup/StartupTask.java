package org.example.startup;

/**
 * Interface for startup tasks that can be triggered manually from the landing page popup.
 * Each task represents a discrete unit of work that was previously auto-run on application startup.
 */
public interface StartupTask {

    /**
     * Unique identifier for this task (e.g., "portfolio-migration").
     */
    String getId();

    /**
     * Human-readable name displayed in the UI (e.g., "Portfolio Migration").
     */
    String getName();

    /**
     * Description of what this task does, shown in the UI.
     */
    String getDescription();

    /**
     * Category for grouping in the UI (e.g., "Portfolio", "Data Sync", "Signals", "Indicators").
     */
    String getCategory();

    /**
     * Whether this task is enabled by default when the popup appears.
     */
    boolean defaultEnabled();

    /**
     * Whether this task is required and cannot be unchecked by the user.
     */
    boolean isRequired();

    /**
     * Execute the task. Called sequentially by the StartupTaskController.
     */
    void execute();
}
