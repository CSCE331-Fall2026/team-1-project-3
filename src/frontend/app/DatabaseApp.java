package frontend.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Starts the JavaFX cashier application.
 * @author Sharaf Mehmood
 */
public class DatabaseApp extends Application {
  /**
   * Creates the application instance.
   */
  public DatabaseApp() {
  }

  /**
   * Loads and displays the cashier view.
   *
   * @param stage the primary stage supplied by JavaFX
   * @throws Exception if the cashier view cannot be loaded
   * @author Sharaf Mehmood
   */
  @Override
  public void start(Stage stage) throws Exception {
    FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/frontend/resources/cashierGUI.fxml"));
    Scene scene = new Scene(fxmlLoader.load(), 1200, 800);
    stage.setTitle("Cashier View");
    stage.setScene(scene);
    stage.show();
  }

   /**
    * Launches the JavaFX application.
    *
    * @param args command-line arguments passed to the application
    * @author Sharaf Mehmood
    */
   public static void main(String[] args) {
      launch();
   }
}
