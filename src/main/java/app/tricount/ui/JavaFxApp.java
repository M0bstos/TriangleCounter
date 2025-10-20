package app.tricount.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class JavaFxApp extends Application {
  @Override
  public void start(Stage stage) throws Exception {
    FXMLLoader loader = new FXMLLoader(JavaFxApp.class.getResource("/app/tricount/ui/MainView.fxml"));
    Parent root = loader.load();
    Scene scene = new Scene(root, 1024, 768);
    stage.setTitle("Triangle Counter");
    stage.setScene(scene);
    stage.show();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
