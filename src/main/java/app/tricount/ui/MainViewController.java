package app.tricount.ui;

import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.control.ToolBar;

public final class MainViewController {
  @FXML
  private BorderPane root;

  @FXML
  private ToolBar toolbar;

  @FXML
  private Pane canvasPane;

  @FXML
  private VBox sidePanel;

  public BorderPane getRoot() {
    return root;
  }

  public ToolBar getToolbar() {
    return toolbar;
  }

  public Pane getCanvasPane() {
    return canvasPane;
  }

  public VBox getSidePanel() {
    return sidePanel;
  }
}
