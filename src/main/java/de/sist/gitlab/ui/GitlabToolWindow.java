package de.sist.gitlab.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.messages.MessageBus;
import de.sist.gitlab.BackgroundUpdateService;
import de.sist.gitlab.DateTime;
import de.sist.gitlab.GitlabService;
import de.sist.gitlab.PipelineJobStatus;
import de.sist.gitlab.ReloadListener;
import de.sist.gitlab.StatusFilter;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import javax.swing.text.BadLocationException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"ConstantConditions", "Convert2Lambda"})
public class GitlabToolWindow {

    Logger logger = Logger.getInstance(GitlabToolWindow.class);

    private JPanel toolWindowContent;
    private JTable pipelineTable;
    private JScrollPane tableScrollPane;
    private JPanel tablePanel;
    private boolean initialLoad = true;

    private PipelineTableModel tableModel;

    private final GitlabService gitlabService;
    private final BackgroundUpdateService backgroundUpdateService;
    private final MessageBus messageBus;
    private final StatusFilter statusFilter;
    private TableRowSorter<PipelineTableModel> tableSorter;

    public GitlabToolWindow(Project project) {
        gitlabService = project.getService(GitlabService.class);
        backgroundUpdateService = project.getService(BackgroundUpdateService.class);
        messageBus = project.getMessageBus();
        statusFilter = project.getService(StatusFilter.class);

        messageBus.connect().subscribe(ReloadListener.RELOAD, statuses -> {
            ApplicationManager.getApplication().invokeLater(() ->
                    showPipelines(statuses));
        });

        tableModel = new PipelineTableModel();
        pipelineTable = new JBTable(tableModel) {
            @Override
            public TableCellRenderer getCellRenderer(int row, int column) {
                TableCellRenderer cellRenderer = super.getCellRenderer(row, column);
                if (column == 1) {
                    return getStatusCellRenderer();
                }
                if (column == 2) {
                    return getDateCellRenderer();
                }
                if (column == 3) {
                    return getLinkCellRenderer();
                }
                return cellRenderer;
            }

            @NotNull
            @Override
            public Component prepareRenderer(@NotNull TableCellRenderer renderer, int rowIndex, int columnIndex) {
                Component component = super.prepareRenderer(renderer, rowIndex, columnIndex);
                int rendererWidth = component.getPreferredSize().width;
                TableColumn column = getColumnModel().getColumn(columnIndex);
                column.setPreferredWidth(Math.max(rendererWidth + getIntercellSpacing().width, column.getPreferredWidth()));

                return component;
            }
        };
        pipelineTable.setAutoCreateRowSorter(true);
        pipelineTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        pipelineTable.setUpdateSelectionOnSort(true);

        MouseAdapter urlMouseAdapter = getUrlMouseAdapter();
        pipelineTable.addMouseListener(urlMouseAdapter);
        pipelineTable.addMouseMotionListener(urlMouseAdapter);

        pipelineTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pipelineTable.setCellSelectionEnabled(true);

        createTablePanel(project);
    }

    private void createTablePanel(Project project) {
        AnActionButton refreshActionButton = new AnActionButton("Refresh", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                runLoadPipelinesTask(project);
            }

            @Override
            public JComponent getContextComponent() {
                return pipelineTable;
            }

        };
        DefaultActionGroup actionGroup = new DefaultActionGroup(refreshActionButton);

        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, actionGroup, true);

        JPanel panel = new JPanel(new MigLayout("ins 0, fill", "[left]0[left, fill]push[right]", "center"));
        panel.add(actionToolbar.getComponent());
        SearchTextField filterField = new SearchTextField(false, null);
        filterField.getTextEditor().setToolTipText("Filter by substrings of branch names");

        filterField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                try {
                    String text = e.getDocument().getText(0, e.getDocument().getLength());
                    if (text == null || text.isEmpty()) {
                        tableSorter.setRowFilter(null);
                    } else {
                        RowFilter<PipelineTableModel, Integer> filter = new RowFilter<PipelineTableModel, Integer>() {
                            @Override
                            public boolean include(Entry<? extends PipelineTableModel, ? extends Integer> entry) {
                                return entry.getModel().rows.get(entry.getIdentifier()).branchName.toLowerCase().contains(text.toLowerCase());
                            }
                        };
                        tableSorter.setRowFilter(filter);
                    }
                } catch (BadLocationException ex) {
                    logger.error(e);
                }
            }
        });
        panel.add(filterField);

        tablePanel.add(panel, BorderLayout.NORTH, 0);
        tablePanel.add(new JBScrollPane(pipelineTable), BorderLayout.CENTER, 1);
    }

    private void runLoadPipelinesTask(Project project) {
        Task.Backgroundable task = new Task.Backgroundable(project, "Loading GitLab Pipelines") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                loadPipelines();
            }
        };
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
    }


    private void loadPipelines() {
        List<PipelineJobStatus> statuses;
        try {
            statuses = gitlabService.getStatuses();
            messageBus.syncPublisher(ReloadListener.RELOAD).reload(statuses);
            backgroundUpdateService.startBackgroundTask();
        } catch (IOException ex) {
            backgroundUpdateService.stopBackgroundTask();
        }
    }

    public void showPipelines(List<PipelineJobStatus> statuses) {
        logger.debug("Showing " + statuses.size() + " statuses in table");
        tableScrollPane.setEnabled(true);

        tableModel.rows.clear();
        tableModel.rows.addAll(getStatusesToShow(statuses));
        tableModel.fireTableDataChanged();

        if (initialLoad) {
            //Prevent resetting the sorting selected by the user on next update
            sortTableByBranchName();
            initialLoad = false;
        }
    }

    /**
     * Returns a list of statuses containing for each branch either all statuses up to the last final run (failed or successful) if such a run exists or otherwise just the latest run.
     *
     * @param statuses List of statuses to filter.
     * @return List of filtered statuses.
     */
    private List<PipelineJobStatus> getStatusesToShow(List<PipelineJobStatus> statuses) {
        List<PipelineJobStatus> newRows = new ArrayList<>();
        statuses = new ArrayList<>(statusFilter.filterPipelines(statuses));
        statuses.sort(Comparator.comparing(x -> ((PipelineJobStatus) x).creationTime).reversed());
        Map<String, List<PipelineJobStatus>> branchesToStatuses = statuses.stream().collect(Collectors.groupingBy(x -> x.branchName));
        for (Map.Entry<String, List<PipelineJobStatus>> entry : branchesToStatuses.entrySet()) {
            Optional<PipelineJobStatus> firstFinalStatus = entry.getValue().stream().filter(this::isFinalStatus).findFirst();
            if (firstFinalStatus.isPresent()) {
                int indexOfFirstFinalStatus = entry.getValue().indexOf(firstFinalStatus.get());
                newRows.addAll(entry.getValue().subList(0, indexOfFirstFinalStatus + 1));
            } else {
                newRows.add(entry.getValue().get(0));
            }
        }
        newRows.sort(Comparator.comparing(x -> ((PipelineJobStatus) x).creationTime).reversed());
        return newRows;
    }

    private boolean isFinalStatus(PipelineJobStatus status) {
        return Stream.of("failed", "success").anyMatch(x -> x.equals(status.result));
    }

    private void sortTableByBranchName() {
        tableSorter = new TableRowSorter<>(tableModel);
        pipelineTable.setRowSorter(tableSorter);
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        sortKeys.add(new RowSorter.SortKey(0, SortOrder.ASCENDING));
        tableSorter.setSortKeys(sortKeys);
        tableSorter.sort();
    }

    public JPanel getContent() {
        return toolWindowContent;
    }

    private void createUIComponents() {
    }

    @NotNull
    private MouseAdapter getUrlMouseAdapter() {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                int viewRow = pipelineTable.getSelectedRow();
                if (viewRow == -1) {
                    return;
                }

                int selectedColumn = pipelineTable.columnAtPoint(e.getPoint());
                if (selectedColumn != 3) {
                    return;
                }

                int modelRow = pipelineTable.convertRowIndexToModel(viewRow);
                String url = (String) tableModel.getValueAt(modelRow, selectedColumn);

                com.intellij.ide.BrowserUtil.browse(url);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int columnIndex = pipelineTable.columnAtPoint(e.getPoint());
                int rowIndex = pipelineTable.rowAtPoint(e.getPoint());
                if (columnIndex == 3 && rowIndex > -1 && rowIndex < tableModel.getRowCount()) {
                    pipelineTable.setCursor(new Cursor(Cursor.HAND_CURSOR));
                } else {
                    pipelineTable.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }
        };
    }

    private TableCellRenderer getLinkCellRenderer() {
        return new TableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = new JLabel((String) value);
                label.setForeground(JBColor.BLUE);
                Map<TextAttribute, Object> attributes = new HashMap<>(label.getFont().getAttributes());
                attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
                label.setFont(label.getFont().deriveFont(attributes));
                return label;
            }
        };
    }


    private TableCellRenderer getDateCellRenderer() {
        return new TableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                LocalDateTime dateTime = (LocalDateTime) value;
                return new JLabel(DateTime.formatDateTime(dateTime));
            }
        };
    }

    private TableCellRenderer getStatusCellRenderer() {
        return new TableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                String status = (String) value;
                JBLabel label = new JBLabel(status);
                switch (status) {
                    case "running":
                        label.setForeground(JBColor.ORANGE);
                        break;
                    case "pending":
                        label.setForeground(JBColor.GRAY);
                        break;
                    case "success":
                        label.setForeground(JBColor.GREEN);
                        break;
                    case "failed":
                        label.setForeground(JBColor.RED);
                        break;
                    case "skipped":
                    case "canceled":
                        label.setForeground(JBColor.BLUE);
                        break;
                }
                return label;
            }
        };
    }

    private static class PipelineTableModel extends AbstractTableModel {

        public List<PipelineJobStatus> rows = new ArrayList<>();
        public List<TableRowDefinition> definitions = Arrays.asList(
                new TableRowDefinition("Branch", x -> x.branchName),
                new TableRowDefinition("Result", x -> x.result),
                new TableRowDefinition("Time", x -> x.creationTime),
                new TableRowDefinition("Link", x -> x.pipelineLink)
        );

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return definitions.size();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PipelineJobStatus row = rows.get(rowIndex);
            return definitions.get(columnIndex).tableModelRowFunction.apply(row);
        }

        @Override
        public String getColumnName(int columnIndex) {
            return definitions.get(columnIndex).title;
        }

    }


    private static class TableRowDefinition {
        public String title;
        public Function<PipelineJobStatus, Object> tableModelRowFunction;

        public TableRowDefinition(String title, Function<PipelineJobStatus, Object> tableModelRowFunction) {
            this.title = title;
            this.tableModelRowFunction = tableModelRowFunction;
        }
    }
}
