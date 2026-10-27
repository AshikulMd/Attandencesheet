package com.example.attendancesheet;

import javafx.application.Application;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

public class Newsheet extends Application {

    public Newsheet() {
    }

    private boolean isDarkMode = false;

    // Data Models
    private static class CourseData {
        String courseName;
        String professorName;
        int totalWeeks;
        ObservableList<Student> studentsList = FXCollections.observableArrayList();

        CourseData(String courseName, String professorName, int totalWeeks) {
            this.courseName = courseName;
            this.professorName = professorName;
            this.totalWeeks = totalWeeks;

            // Default blank grid of 20 editable rows ready for typing
            for (int i = 0; i < 20; i++) {
                studentsList.add(new Student("", ""));
            }
        }
    }

    public static class Student {
        private final StringProperty id = new SimpleStringProperty("");
        private final StringProperty name = new SimpleStringProperty("");
        private final Map<Integer, StringProperty> attendanceMarks = new HashMap<>();
        private final StringProperty overallPercent = new SimpleStringProperty("0%");

        public Student(String id, String name) {
            this.id.set(id);
            this.name.set(name);
        }

        public StringProperty idProperty() { return id; }
        public StringProperty nameProperty() { return name; }
        public StringProperty overallPercentProperty() { return overallPercent; }

        public StringProperty markProperty(int sessionIndex) {
            return attendanceMarks.computeIfAbsent(sessionIndex, k -> new SimpleStringProperty(""));
        }
    }

    private final Map<String, CourseData> coursesMap = new HashMap<>();
    private CourseData currentCourse;

    // UI Controls
    private BorderPane mainLayout;
    private VBox sidebar;
    private BorderPane mainContent;
    private TableView<Student> tableView;
    private ComboBox<String> comboCourseSelector;
    private ComboBox<String> comboFilterStatus;
    private TextField txtSearch;
    private Button btnThemeToggle;

    // Sidebar Labels
    private Label lblHeaderSidebar, lblCourseSidebar, lblProfSidebar, lblStudentCount, lblWeeksCount, lblHours, lblTotalSessions;
    private Label lblSubTitle, lblStatAvgRate, lblStatPresent, lblStatAbsent;

    // Stat Cards References
    private final List<VBox> cardList = new ArrayList<>();
    private final List<Label> cardTitleLabels = new ArrayList<>();
    private final List<Label> cardValLabels = new ArrayList<>();

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Student Attendance Management System - Newsheet");

        CourseData defaultCourse = new CourseData("Comprehensive Design of Data Structures", "Dr. Lord", 4);
        coursesMap.put(defaultCourse.courseName, defaultCourse);
        currentCourse = defaultCourse;

        mainLayout = new BorderPane();

        sidebar = buildSidebar();
        mainContent = buildMainContent();

        mainLayout.setLeft(sidebar);
        mainLayout.setCenter(mainContent);

        Scene scene = new Scene(mainLayout, 1400, 850);

        primaryStage.setScene(scene);
        primaryStage.show();

        applyTheme(scene);
        rebuildTable();
        calculateStatistics();
    }

    private VBox buildSidebar() {
        VBox box = new VBox(8);
        box.setPrefWidth(260);
        box.setPadding(new Insets(20, 15, 20, 15));

        lblHeaderSidebar = new Label("CURRENT COURSE");
        lblHeaderSidebar.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));

        lblCourseSidebar = new Label(currentCourse.courseName);
        lblCourseSidebar.setFont(Font.font("SansSerif", FontWeight.BOLD, 14));
        lblCourseSidebar.setWrapText(true);

        lblProfSidebar = new Label("Professor: " + currentCourse.professorName);
        lblProfSidebar.setFont(Font.font("SansSerif", FontWeight.NORMAL, 12));

        lblStudentCount = new Label("Students: " + getActiveStudentCount());
        lblWeeksCount = new Label("Weeks: " + currentCourse.totalWeeks);
        lblHours = new Label("Hours/Week: 4");
        lblTotalSessions = new Label("Total Sessions: " + (currentCourse.totalWeeks * 4));

        Separator sep = new Separator();

        Button btnAddMultipleRows = createSidebarButton("➕ Add N Rows...");
        Button btnUndoRow = createSidebarButton("↩ Undo Last Row");
        Button btnDeleteStudent = createSidebarButton("🗑 Delete Selected Row");
        Button btnAddWeek = createSidebarButton("📅 Add Week");
        Button btnRemoveWeek = createSidebarButton("➖ Remove Week");
        Button btnExportCSV = createSidebarButton("📤 Export to CSV");
        Button btnAddNewCourse = createSidebarButton("📚 Create New Course");

        btnAddMultipleRows.setOnAction(e -> addMultipleRows());
        btnUndoRow.setOnAction(e -> undoLastRow());
        btnDeleteStudent.setOnAction(e -> deleteSelectedStudent());
        btnAddWeek.setOnAction(e -> addWeek());
        btnRemoveWeek.setOnAction(e -> removeWeek());
        btnExportCSV.setOnAction(e -> exportToCSV());
        btnAddNewCourse.setOnAction(e -> addNewCourse());

        Region spacer = new Region();
        spacer.setPrefHeight(5);

        box.getChildren().addAll(
                lblHeaderSidebar, lblCourseSidebar, lblProfSidebar,
                spacer,
                lblStudentCount, lblWeeksCount, lblHours, lblTotalSessions,
                sep,
                btnAddMultipleRows, btnUndoRow, btnDeleteStudent, btnAddWeek, btnRemoveWeek, btnExportCSV, btnAddNewCourse
        );

        return box;
    }

    private BorderPane buildMainContent() {
        BorderPane content = new BorderPane();

        BorderPane topBar = new BorderPane();
        topBar.setPadding(new Insets(15, 20, 10, 20));

        VBox titleBox = new VBox(3);
        Label lblTitle = new Label("Student Attendance Management System");
        lblTitle.setFont(Font.font("SansSerif", FontWeight.BOLD, 20));

        lblSubTitle = new Label(currentCourse.courseName);
        lblSubTitle.setFont(Font.font("SansSerif", 13));

        titleBox.getChildren().addAll(lblTitle, lblSubTitle);

        HBox navButtons = new HBox(10);
        navButtons.setAlignment(Pos.CENTER_RIGHT);

        comboCourseSelector = new ComboBox<>(FXCollections.observableArrayList(coursesMap.keySet()));
        comboCourseSelector.setValue(currentCourse.courseName);
        comboCourseSelector.setOnAction(e -> {
            String sel = comboCourseSelector.getValue();
            if (sel != null && coursesMap.containsKey(sel)) switchCourse(coursesMap.get(sel));
        });

        Button btnSettings = createNavButton("Course Settings");
        btnThemeToggle = createNavButton("🌙 Dark Mode");

        btnSettings.setOnAction(e -> showAlert("Course Settings", "Current Course: " + currentCourse.courseName + "\nProfessor: " + currentCourse.professorName, Alert.AlertType.INFORMATION));
        btnThemeToggle.setOnAction(e -> toggleTheme());

        navButtons.getChildren().addAll(new Label("Active Course:"), comboCourseSelector, btnSettings, btnThemeToggle);

        topBar.setLeft(titleBox);
        topBar.setRight(navButtons);

        HBox cardsBox = new HBox(15);
        cardsBox.setPadding(new Insets(5, 20, 10, 20));
        HBox.setHgrow(cardsBox, Priority.ALWAYS);

        lblStatAvgRate = new Label("0%");
        lblStatPresent = new Label("0");
        lblStatAbsent = new Label("0");

        cardsBox.getChildren().addAll(
                createCard("Class Attendance Rate", lblStatAvgRate, "#4CAF50"),
                createCard("Total Present Sessions", lblStatPresent, "#2196F3"),
                createCard("Total Absent Sessions", lblStatAbsent, "#F44336")
        );

        HBox filterBox = new HBox(15);
        filterBox.setPadding(new Insets(0, 20, 10, 20));
        filterBox.setAlignment(Pos.CENTER_LEFT);

        txtSearch = new TextField();
        txtSearch.setPromptText("Search ID or Name...");
        txtSearch.textProperty().addListener((obs, oldV, newV) -> applyFilters());

        comboFilterStatus = new ComboBox<>(FXCollections.observableArrayList(
                "Show All", "Has Absences (✗)", "100% Present", "Low Attendance (<75%)"
        ));
        comboFilterStatus.setValue("Show All");
        comboFilterStatus.setOnAction(e -> applyFilters());

        Button btnDate = new Button("📅 Pick Session Date");
        btnDate.setOnAction(e -> pickSessionDate());

        filterBox.getChildren().addAll(new Label("Search Student:"), txtSearch, new Label("Status Filter:"), comboFilterStatus, btnDate);

        VBox northBox = new VBox(topBar, cardsBox, filterBox);

        tableView = new TableView<>();
        tableView.setEditable(true);

        content.setTop(northBox);
        content.setCenter(tableView);
        BorderPane.setMargin(tableView, new Insets(0, 20, 20, 20));

        return content;
    }

    private VBox createCard(String title, Label valLabel, String hexColor) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(10, 15, 10, 15));
        HBox.setHgrow(card, Priority.ALWAYS);

        Label titleLbl = new Label(title);
        titleLbl.setFont(Font.font("SansSerif", FontWeight.BOLD, 11));

        valLabel.setFont(Font.font("SansSerif", FontWeight.BOLD, 22));

        card.getChildren().addAll(titleLbl, valLabel);

        cardList.add(card);
        cardTitleLabels.add(titleLbl);
        cardValLabels.add(valLabel);

        updateCardStyle(card, titleLbl, valLabel, hexColor);

        return card;
    }

    private void updateCardStyle(VBox card, Label titleLbl, Label valLabel, String hexColor) {
        if (isDarkMode) {
            card.setStyle("-fx-background-color: #2D1B36; " +
                    "-fx-background-radius: 6; " +
                    "-fx-border-color: " + hexColor + "; " +
                    "-fx-border-width: 0 0 0 4; " +
                    "-fx-border-radius: 6;");
            titleLbl.setTextFill(Color.web("#D2C8DC"));
            valLabel.setTextFill(Color.web("#FFFFFF"));
        } else {
            card.setStyle("-fx-background-color: #FFFFFF; " +
                    "-fx-background-radius: 6; " +
                    "-fx-border-color: " + hexColor + "; " +
                    "-fx-border-width: 0 0 0 4; " +
                    "-fx-border-radius: 6;");
            titleLbl.setTextFill(Color.web("#666666"));
            valLabel.setTextFill(Color.web("#1A1A1A"));
        }
    }

    @SuppressWarnings("unchecked")
    private void rebuildTable() {
        tableView.getColumns().clear();

        // Serial Number Column (S.N.)
        TableColumn<Student, String> colSn = new TableColumn<>("S.N.");
        colSn.setPrefWidth(50);
        colSn.setStyle("-fx-alignment: CENTER;");
        colSn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else {
                    setText(String.valueOf(getIndex() + 1));
                }
            }
        });

        // Student ID Column (Direct Inline Editing)
        TableColumn<Student, String> colId = new TableColumn<>("Student ID");
        colId.setCellValueFactory(data -> data.getValue().idProperty());
        colId.setCellFactory(TextFieldTableCell.forTableColumn());
        colId.setOnEditCommit(e -> {
            e.getRowValue().idProperty().set(e.getNewValue());
            lblStudentCount.setText("Students: " + getActiveStudentCount());
            calculateStatistics();
        });
        colId.setPrefWidth(120);

        // Student Name Column (Direct Inline Editing)
        TableColumn<Student, String> colName = new TableColumn<>("Student Name");
        colName.setCellValueFactory(data -> data.getValue().nameProperty());
        colName.setCellFactory(TextFieldTableCell.forTableColumn());
        colName.setOnEditCommit(e -> {
            e.getRowValue().nameProperty().set(e.getNewValue());
            lblStudentCount.setText("Students: " + getActiveStudentCount());
            calculateStatistics();
        });
        colName.setPrefWidth(180);

        tableView.getColumns().addAll(colSn, colId, colName);

        for (int w = 1; w <= currentCourse.totalWeeks; w++) {
            TableColumn<Student, String> weekGroup = new TableColumn<>("WEEK " + w);

            for (int h = 1; h <= 4; h++) {
                int sessionIdx = (w - 1) * 4 + (h - 1);
                TableColumn<Student, String> colH = new TableColumn<>("H" + h);
                colH.setCellValueFactory(data -> data.getValue().markProperty(sessionIdx));
                colH.setPrefWidth(55);
                colH.setStyle("-fx-alignment: CENTER;");

                colH.setCellFactory(column -> new TableCell<>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setStyle("");
                            setOnMouseClicked(null);
                            setContextMenu(null);
                        } else {
                            setText(item);
                            if ("✓".equals(item)) {
                                setStyle(isDarkMode ? "-fx-background-color: #145A28; -fx-text-fill: #B4FFB4;"
                                        : "-fx-background-color: #DCF5E1; -fx-text-fill: #006400;");
                            } else if ("✗".equals(item)) {
                                setStyle(isDarkMode ? "-fx-background-color: #6E1E1E; -fx-text-fill: #FFB4B4;"
                                        : "-fx-background-color: #FFDCDC; -fx-text-fill: RED;");
                            } else {
                                setStyle("");
                            }

                            setOnMouseClicked(event -> {
                                Student student = getTableRow().getItem();
                                if (student == null) return;

                                if (event.getButton() == MouseButton.PRIMARY) {
                                    if (event.getClickCount() == 1) {
                                        student.markProperty(sessionIdx).set("✓");
                                    } else if (event.getClickCount() == 2) {
                                        student.markProperty(sessionIdx).set("✗");
                                    }
                                    updateStudentPercentages(student);
                                    calculateStatistics();
                                    tableView.refresh();
                                }
                            });

                            ContextMenu contextMenu = new ContextMenu();
                            MenuItem itemPresent = new MenuItem("✓ Present");
                            MenuItem itemAbsent = new MenuItem("✗ Absent");
                            MenuItem itemClear = new MenuItem(" Clear");

                            itemPresent.setOnAction(e -> {
                                Student student = getTableRow().getItem();
                                if (student != null) {
                                    student.markProperty(sessionIdx).set("✓");
                                    updateStudentPercentages(student);
                                    calculateStatistics();
                                    tableView.refresh();
                                }
                            });

                            itemAbsent.setOnAction(e -> {
                                Student student = getTableRow().getItem();
                                if (student != null) {
                                    student.markProperty(sessionIdx).set("✗");
                                    updateStudentPercentages(student);
                                    calculateStatistics();
                                    tableView.refresh();
                                }
                            });

                            itemClear.setOnAction(e -> {
                                Student student = getTableRow().getItem();
                                if (student != null) {
                                    student.markProperty(sessionIdx).set("");
                                    updateStudentPercentages(student);
                                    calculateStatistics();
                                    tableView.refresh();
                                }
                            });

                            contextMenu.getItems().addAll(itemPresent, itemAbsent, new SeparatorMenuItem(), itemClear);
                            setContextMenu(contextMenu);
                        }
                    }
                });

                weekGroup.getColumns().add(colH);
            }

            int finalW = w;
            TableColumn<Student, String> colWeekPct = new TableColumn<>("Week %");
            colWeekPct.setPrefWidth(60);
            colWeekPct.setStyle("-fx-alignment: CENTER;");
            colWeekPct.setCellValueFactory(data -> {
                Student s = data.getValue();
                int start = (finalW - 1) * 4;
                int count = 0;
                for (int i = start; i < start + 4; i++) {
                    if ("✓".equals(s.markProperty(i).get())) count++;
                }
                return new SimpleStringProperty(((int) ((count / 4.0) * 100)) + "%");
            });

            weekGroup.getColumns().add(colWeekPct);
            tableView.getColumns().add(weekGroup);
        }

        TableColumn<Student, String> colOverall = new TableColumn<>("Overall %");
        colOverall.setCellValueFactory(data -> data.getValue().overallPercentProperty());
        colOverall.setPrefWidth(75);
        colOverall.setStyle("-fx-alignment: CENTER;");

        tableView.getColumns().add(colOverall);
        tableView.setItems(currentCourse.studentsList);
    }

    private void addMultipleRows() {
        TextInputDialog dialog = new TextInputDialog("5");
        dialog.setTitle("Add Multiple Rows");
        dialog.setHeaderText("Expand Table Capacity");
        dialog.setContentText("How many empty rows would you like to add?");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(countStr -> {
            try {
                int count = Integer.parseInt(countStr.trim());
                for (int i = 0; i < count; i++) {
                    currentCourse.studentsList.add(new Student("", ""));
                }
                lblStudentCount.setText("Students: " + getActiveStudentCount());
            } catch (NumberFormatException ignored) {
                showAlert("Invalid Input", "Please enter a valid number.", Alert.AlertType.ERROR);
            }
        });
    }

    private void undoLastRow() {
        if (!currentCourse.studentsList.isEmpty()) {
            currentCourse.studentsList.remove(currentCourse.studentsList.size() - 1);
            lblStudentCount.setText("Students: " + getActiveStudentCount());
            calculateStatistics();
        } else {
            showAlert("Info", "No rows left to remove.", Alert.AlertType.INFORMATION);
        }
    }

    private void updateStudentPercentages(Student s) {
        int totalSessions = currentCourse.totalWeeks * 4;
        int totalPresent = 0;
        for (int i = 0; i < totalSessions; i++) {
            if ("✓".equals(s.markProperty(i).get())) totalPresent++;
        }
        int pct = (int) (((double) totalPresent / totalSessions) * 100);
        s.overallPercentProperty().set(pct + "%");
    }

    private void calculateStatistics() {
        int totalPresent = 0;
        int totalAbsent = 0;
        int totalSessions = currentCourse.totalWeeks * 4;

        for (Student s : currentCourse.studentsList) {
            if (!s.nameProperty().get().trim().isEmpty() || !s.idProperty().get().trim().isEmpty()) {
                for (int i = 0; i < totalSessions; i++) {
                    String val = s.markProperty(i).get();
                    if ("✓".equals(val)) totalPresent++;
                    else if ("✗".equals(val)) totalAbsent++;
                }
            }
        }

        int totalMarked = totalPresent + totalAbsent;
        int rate = totalMarked > 0 ? (int) (((double) totalPresent / totalMarked) * 100) : 0;

        lblStatAvgRate.setText(rate + "%");
        lblStatPresent.setText(String.valueOf(totalPresent));
        lblStatAbsent.setText(String.valueOf(totalAbsent));
    }

    private void applyFilters() {
        String query = txtSearch.getText().toLowerCase().trim();
        String filter = comboFilterStatus.getValue();

        ObservableList<Student> filtered = FXCollections.observableArrayList();

        for (Student s : currentCourse.studentsList) {
            boolean matchesSearch = query.isEmpty() ||
                    s.idProperty().get().toLowerCase().contains(query) ||
                    s.nameProperty().get().toLowerCase().contains(query);

            if (!matchesSearch) continue;

            if ("Show All".equals(filter)) {
                filtered.add(s);
            } else if ("100% Present".equals(filter) && "100%".equals(s.overallPercentProperty().get())) {
                filtered.add(s);
            } else if ("Low Attendance (<75%)".equals(filter)) {
                int pct = Integer.parseInt(s.overallPercentProperty().get().replace("%", ""));
                if (pct < 75) filtered.add(s);
            } else if ("Has Absences (✗)".equals(filter)) {
                boolean hasAbsent = false;
                for (int i = 0; i < currentCourse.totalWeeks * 4; i++) {
                    if ("✗".equals(s.markProperty(i).get())) { hasAbsent = true; break; }
                }
                if (hasAbsent) filtered.add(s);
            }
        }
        tableView.setItems(filtered);
    }

    private void exportToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Attendance CSV");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        fileChooser.setInitialFileName(currentCourse.courseName.replaceAll("\\s+", "_") + "_Attendance.csv");

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("S.N.,Student ID,Student Name,Overall Attendance\n");
                int sn = 1;
                for (Student s : currentCourse.studentsList) {
                    if (!s.nameProperty().get().isEmpty()) {
                        writer.write(sn++ + "," + s.idProperty().get() + "," + s.nameProperty().get() + "," + s.overallPercentProperty().get() + "\n");
                    }
                }
                showAlert("Export Success", "Attendance saved to: " + file.getAbsolutePath(), Alert.AlertType.INFORMATION);
            } catch (IOException e) {
                showAlert("Export Error", "Failed to write CSV file: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    private void toggleTheme() {
        isDarkMode = !isDarkMode;
        btnThemeToggle.setText(isDarkMode ? "☀️ Light Mode" : "🌙 Dark Mode");
        applyTheme(mainLayout.getScene());
        rebuildTable();
    }

    private void applyTheme(Scene scene) {
        if (isDarkMode) {
            scene.getRoot().setStyle("-fx-base: #1E1022; -fx-background-color: #18161E;");
            sidebar.setStyle("-fx-background-color: #1E1022;");

            lblHeaderSidebar.setTextFill(Color.web("#E6C2FF"));
            lblCourseSidebar.setTextFill(Color.web("#FFFFFF"));
            lblProfSidebar.setTextFill(Color.web("#D2C8DC"));
            lblStudentCount.setTextFill(Color.web("#D2C8DC"));
            lblWeeksCount.setTextFill(Color.web("#D2C8DC"));
            lblHours.setTextFill(Color.web("#D2C8DC"));
            lblTotalSessions.setTextFill(Color.web("#D2C8DC"));
        } else {
            scene.getRoot().setStyle("-fx-base: #F8F5FC; -fx-background-color: #FAF8FC;");
            sidebar.setStyle("-fx-background-color: #F3E8FF; -fx-border-color: #E2D3F5; -fx-border-width: 0 1 0 0;");

            lblHeaderSidebar.setTextFill(Color.web("#6B21A8"));
            lblCourseSidebar.setTextFill(Color.web("#2A0845"));
            lblProfSidebar.setTextFill(Color.web("#4A154B"));
            lblStudentCount.setTextFill(Color.web("#4A154B"));
            lblWeeksCount.setTextFill(Color.web("#4A154B"));
            lblHours.setTextFill(Color.web("#4A154B"));
            lblTotalSessions.setTextFill(Color.web("#4A154B"));
        }

        String[] borderColors = {"#4CAF50", "#2196F3", "#F44336"};
        for (int i = 0; i < cardList.size(); i++) {
            updateCardStyle(cardList.get(i), cardTitleLabels.get(i), cardValLabels.get(i), borderColors[i % 3]);
        }
    }

    private void switchCourse(CourseData course) {
        this.currentCourse = course;
        lblSubTitle.setText(course.courseName);
        lblCourseSidebar.setText(course.courseName);
        lblProfSidebar.setText("Professor: " + course.professorName);
        lblStudentCount.setText("Students: " + getActiveStudentCount());
        lblWeeksCount.setText("Weeks: " + course.totalWeeks);
        lblTotalSessions.setText("Total Sessions: " + (course.totalWeeks * 4));
        rebuildTable();
        calculateStatistics();
    }

    private int getActiveStudentCount() {
        return (int) currentCourse.studentsList.stream()
                .filter(s -> !s.nameProperty().get().isEmpty() || !s.idProperty().get().isEmpty()).count();
    }

    private void deleteSelectedStudent() {
        Student selected = tableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            currentCourse.studentsList.remove(selected);
            lblStudentCount.setText("Students: " + getActiveStudentCount());
            calculateStatistics();
        } else {
            showAlert("Warning", "Please select a row to delete.", Alert.AlertType.WARNING);
        }
    }

    private void addWeek() {
        currentCourse.totalWeeks++;
        lblWeeksCount.setText("Weeks: " + currentCourse.totalWeeks);
        lblTotalSessions.setText("Total Sessions: " + (currentCourse.totalWeeks * 4));
        rebuildTable();
    }

    private void removeWeek() {
        if (currentCourse.totalWeeks > 1) {
            currentCourse.totalWeeks--;
            lblWeeksCount.setText("Weeks: " + currentCourse.totalWeeks);
            lblTotalSessions.setText("Total Sessions: " + (currentCourse.totalWeeks * 4));
            rebuildTable();
        }
    }

    private void addNewCourse() {
        Dialog<CourseData> dialog = new Dialog<>();
        dialog.setTitle("Create New Course");
        dialog.setHeaderText("Enter Course & Professor Information");

        ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField txtCourseName = new TextField();
        txtCourseName.setPromptText("Course Name");
        TextField txtProfName = new TextField();
        txtProfName.setPromptText("Professor Name");

        grid.add(new Label("Course Name:"), 0, 0);
        grid.add(txtCourseName, 1, 0);
        grid.add(new Label("Professor Name:"), 0, 1);
        grid.add(txtProfName, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                String cName = txtCourseName.getText().trim();
                String pName = txtProfName.getText().trim();
                if (!cName.isEmpty()) {
                    return new CourseData(cName, pName.isEmpty() ? "Instructor" : pName, 4);
                }
            }
            return null;
        });

        Optional<CourseData> result = dialog.showAndWait();
        result.ifPresent(newCourse -> {
            coursesMap.put(newCourse.courseName, newCourse);
            comboCourseSelector.getItems().add(newCourse.courseName);
            comboCourseSelector.setValue(newCourse.courseName);
            switchCourse(newCourse);
        });
    }

    private void pickSessionDate() {
        DatePicker datePicker = new DatePicker(LocalDate.now());
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Pick Session Date");
        dialog.setHeaderText("Choose Date:");
        dialog.getDialogPane().setContent(datePicker);
        dialog.showAndWait();
    }

    private Button createSidebarButton(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setStyle("-fx-background-color: #8B5CF6; -fx-text-fill: white; -fx-alignment: CENTER-LEFT; -fx-font-weight: bold;");
        return btn;
    }

    private Button createNavButton(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #E6E6FA; -fx-border-color: #C8B9D7;");
        return btn;
    }

    private void showAlert(String title, String msg, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}