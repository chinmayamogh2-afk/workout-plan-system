package com.devops.workout.selenium;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.*;
import org.testng.ITestResult;
import org.testng.annotations.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.testng.Assert.*;

/**
 * Selenium WebDriver end-to-end tests for FitFlow.
 *
 * Prerequisites:
 *   - Application running at APP_BASE_URL (default: http://localhost:8080)
 *   - Chrome/Chromium installed
 *
 * Run via Maven:
 *   mvn failsafe:integration-test -Dapp.base.url=http://localhost:8080 -Dselenium.headless=true
 */
public class WorkoutPlanSeleniumTest {

    private WebDriver driver;
    private WebDriverWait wait;
    private String baseUrl;
    private static final String SCREENSHOTS_DIR = "target/selenium-screenshots";
    // Credentials come from -Dselenium.*.password (injected by CI); defaults match local dev seeds.
    private static final String ADMIN_PASSWORD = System.getProperty("selenium.admin.password", "Admin@123");
    private static final String TRAINER_PASSWORD = System.getProperty("selenium.trainer.password", "Trainer@123");

    @BeforeClass
    public void setupClass() throws IOException {
        // Auto-download and configure the ChromeDriver matching installed Chrome
        WebDriverManager.chromedriver().setup();
        Files.createDirectories(Path.of(SCREENSHOTS_DIR));
    }

    private void createScreenshotsDir() {
        try {
            Files.createDirectories(Path.of(SCREENSHOTS_DIR));
        } catch (IOException e) {
            System.err.println("Could not create screenshots directory: " + e.getMessage());
        }
    }

    @BeforeMethod
    public void setup() {
        createScreenshotsDir();
        baseUrl = System.getProperty("app.base.url", "http://localhost:8080");
        boolean headless = Boolean.parseBoolean(System.getProperty("selenium.headless", "false"));

        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage");
        }
        options.addArguments("--window-size=1400,900", "--disable-gpu");

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        driver.manage().window().setSize(new Dimension(1400, 900));
    }

    @AfterMethod
    public void teardown(ITestResult result) {
        if (result.getStatus() == ITestResult.FAILURE) {
            captureScreenshot("FAILED_" + result.getName());
        }
        if (driver != null) {
            driver.quit();
        }
    }

    // ------------------------------------------------------------------ //
    //  Helper Methods                                                       //
    // ------------------------------------------------------------------ //

    private void login(String username, String password) {
        driver.get(baseUrl + "/login");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("loginForm")));

        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.id("loginBtn")).click();

        // Wait for redirect to dashboard
        wait.until(ExpectedConditions.urlContains("/dashboard"));
    }

    private void captureScreenshot(String name) {
        try {
            TakesScreenshot ts = (TakesScreenshot) driver;
            File src = ts.getScreenshotAs(OutputType.FILE);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path dest = Path.of(SCREENSHOTS_DIR, name + "_" + timestamp + ".png");
            Files.copy(src.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Screenshot saved: " + dest);
        } catch (Exception e) {
            System.err.println("Screenshot capture failed: " + e.getMessage());
        }
    }

    private WebElement waitFor(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    // ------------------------------------------------------------------ //
    //  Test 1: Login with valid credentials                                 //
    // ------------------------------------------------------------------ //

    @Test(priority = 1, description = "Verify successful login with admin credentials")
    public void testLoginSuccess() {
        driver.get(baseUrl + "/login");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("loginForm")));

        // Verify login page loads
        assertTrue(driver.getTitle().contains("Login"));

        driver.findElement(By.id("username")).sendKeys("admin");
        driver.findElement(By.id("password")).sendKeys(ADMIN_PASSWORD);
        captureScreenshot("Login_BeforeSubmit");
        driver.findElement(By.id("loginBtn")).click();

        // Verify redirect to dashboard
        wait.until(ExpectedConditions.urlContains("/dashboard"));
        assertTrue(driver.getCurrentUrl().contains("/dashboard"),
                "Should redirect to dashboard after login");

        captureScreenshot("Login_Dashboard");
        System.out.println("✅ Test 1 PASSED: Login successful");
    }

    // ------------------------------------------------------------------ //
    //  Test 2: Login with invalid credentials                               //
    // ------------------------------------------------------------------ //

    @Test(priority = 2, description = "Verify error shown for invalid login credentials")
    public void testLoginFailure() {
        driver.get(baseUrl + "/login");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("loginForm")));

        driver.findElement(By.id("username")).sendKeys("wronguser");
        driver.findElement(By.id("password")).sendKeys("wrongpass");
        driver.findElement(By.id("loginBtn")).click();

        // Should stay on login page with error
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("loginError")));
        WebElement error = driver.findElement(By.id("loginError"));
        assertTrue(error.isDisplayed(), "Error message should be displayed");

        captureScreenshot("Login_Failure");
        System.out.println("✅ Test 2 PASSED: Login failure handled correctly");
    }

    // ------------------------------------------------------------------ //
    //  Test 3: Create a new workout plan                                    //
    // ------------------------------------------------------------------ //

    @Test(priority = 3, description = "Create a new workout plan as trainer")
    public void testCreateWorkoutPlan() {
        login("trainer", TRAINER_PASSWORD);

        // Navigate to create form
        driver.get(baseUrl + "/workouts/new");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("workoutForm")));

        // Fill in the form
        String uniqueName = "Selenium Test Plan " + System.currentTimeMillis();
        driver.findElement(By.id("nameField")).sendKeys(uniqueName);
        driver.findElement(By.id("trainerField")).clear();
        driver.findElement(By.id("trainerField")).sendKeys("Selenium Trainer");
        driver.findElement(By.id("descField")).sendKeys("Created by Selenium automated test");
        driver.findElement(By.id("exerciseField")).sendKeys("Push-up 3x15\nSquat 3x20\nPlank 2x1min");
        driver.findElement(By.id("durationField")).sendKeys("30");

        Select diffSelect = new Select(driver.findElement(By.id("difficultyField")));
        diffSelect.selectByValue("BEGINNER");

        driver.findElement(By.id("muscleField")).sendKeys("Full Body");

        captureScreenshot("CreatePlan_FilledForm");
        driver.findElement(By.id("saveBtn")).click();

        // Verify redirect to detail page and success message
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("planName")));
        String planName = driver.findElement(By.id("planName")).getText();
        assertTrue(planName.contains("Selenium Test Plan"),
                "Plan name should be displayed on detail page");

        // Verify status is DRAFT
        WebElement statusEl = driver.findElement(By.id("planStatus"));
        assertEquals(statusEl.getText().trim(), "DRAFT");

        captureScreenshot("CreatePlan_Success");
        System.out.println("✅ Test 3 PASSED: Workout plan created successfully");
    }

    // ------------------------------------------------------------------ //
    //  Test 4: Search for workout plans                                     //
    // ------------------------------------------------------------------ //

    @Test(priority = 4, description = "Search workout plans by keyword")
    public void testSearchWorkoutPlans() {
        login("admin", ADMIN_PASSWORD);

        driver.get(baseUrl + "/workouts");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("filterForm")));

        // Search for a keyword
        WebElement searchInput = driver.findElement(By.id("searchInput"));
        searchInput.sendKeys("Selenium");
        captureScreenshot("Search_BeforeSubmit");

        driver.findElement(By.id("applyFilter")).click();

        // Wait for results to refresh
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("filterForm")));

        captureScreenshot("Search_Results");
        System.out.println("✅ Test 4 PASSED: Search executed successfully");
    }

    // ------------------------------------------------------------------ //
    //  Test 5: Edit a workout plan                                          //
    // ------------------------------------------------------------------ //

    @Test(priority = 5, description = "Edit an existing workout plan")
    public void testEditWorkoutPlan() {
        login("admin", ADMIN_PASSWORD);

        // First create a plan to edit
        driver.get(baseUrl + "/workouts/new");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("workoutForm")));

        driver.findElement(By.id("nameField")).sendKeys("Edit Target Plan");
        driver.findElement(By.id("trainerField")).clear();
        driver.findElement(By.id("trainerField")).sendKeys("Admin User");
        driver.findElement(By.id("descField")).sendKeys("Plan to be edited");
        driver.findElement(By.id("exerciseField")).sendKeys("Run 20 min");
        driver.findElement(By.id("durationField")).sendKeys("20");
        new Select(driver.findElement(By.id("difficultyField"))).selectByValue("INTERMEDIATE");
        driver.findElement(By.id("muscleField")).sendKeys("Legs");
        driver.findElement(By.id("saveBtn")).click();

        // Now click Edit
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("editBtn")));
        driver.findElement(By.id("editBtn")).click();

        // Modify the name
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("nameField")));
        WebElement nameField = driver.findElement(By.id("nameField"));
        nameField.clear();
        nameField.sendKeys("Edit Target Plan – UPDATED");

        captureScreenshot("EditPlan_FilledForm");
        driver.findElement(By.id("saveBtn")).click();

        // Verify updated name appears
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("planName")));
        String updatedName = driver.findElement(By.id("planName")).getText();
        assertTrue(updatedName.contains("UPDATED"),
                "Plan name should reflect the update");

        captureScreenshot("EditPlan_Success");
        System.out.println("✅ Test 5 PASSED: Workout plan edited successfully");
    }

    // ------------------------------------------------------------------ //
    //  Test 6: Workflow – submit, approve, publish                          //
    // ------------------------------------------------------------------ //

    @Test(priority = 6, description = "Full workflow: DRAFT → SUBMITTED → APPROVED → PUBLISHED")
    public void testWorkflowTransitions() {
        // Step 1: Trainer creates & submits a plan
        login("trainer", TRAINER_PASSWORD);
        driver.get(baseUrl + "/workouts/new");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("workoutForm")));

        driver.findElement(By.id("nameField")).sendKeys("Workflow Test Plan");
        driver.findElement(By.id("trainerField")).clear();
        driver.findElement(By.id("trainerField")).sendKeys("Trainer User");
        driver.findElement(By.id("descField")).sendKeys("Plan for workflow test");
        driver.findElement(By.id("exerciseField")).sendKeys("Burpee 3x10");
        driver.findElement(By.id("durationField")).sendKeys("15");
        new Select(driver.findElement(By.id("difficultyField"))).selectByValue("ADVANCED");
        driver.findElement(By.id("muscleField")).sendKeys("Full Body");
        driver.findElement(By.id("saveBtn")).click();

        // Submit for review
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("submitBtn")));
        captureScreenshot("Workflow_Draft");
        driver.findElement(By.id("submitBtn")).click();

        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("planStatus"), "SUBMITTED"));
        assertEquals(driver.findElement(By.id("planStatus")).getText().trim(), "SUBMITTED");
        captureScreenshot("Workflow_Submitted");

        // Get current URL to return to after re-login
        String planUrl = driver.getCurrentUrl();

        // Step 2: Logout and login as admin
        driver.findElement(By.id("logoutBtn")).click();
        wait.until(ExpectedConditions.urlContains("/login"));

        login("admin", ADMIN_PASSWORD);
        driver.get(planUrl);

        // Approve
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("approveBtn")));
        captureScreenshot("Workflow_ReadyToApprove");
        driver.findElement(By.id("approveBtn")).click();

        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("planStatus"), "APPROVED"));
        assertEquals(driver.findElement(By.id("planStatus")).getText().trim(), "APPROVED");
        captureScreenshot("Workflow_Approved");

        // Publish
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("publishBtn")));
        driver.findElement(By.id("publishBtn")).click();

        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("planStatus"), "PUBLISHED"));
        assertEquals(driver.findElement(By.id("planStatus")).getText().trim(), "PUBLISHED");
        captureScreenshot("Workflow_Published");

        System.out.println("✅ Test 6 PASSED: Full workflow DRAFT→SUBMITTED→APPROVED→PUBLISHED");
    }

    // ------------------------------------------------------------------ //
    //  Test 7: Dashboard loads correctly                                    //
    // ------------------------------------------------------------------ //

    @Test(priority = 7, description = "Verify dashboard shows stats after plan creation")
    public void testDashboardStats() {
        login("admin", ADMIN_PASSWORD);

        wait.until(ExpectedConditions.urlContains("/dashboard"));
        captureScreenshot("Dashboard_Loaded");

        // Check stat cards are present
        assertTrue(driver.findElements(By.cssSelector(".stat-card")).size() > 0,
                "Dashboard should display stat cards");

        // Check recent plans section exists
        assertTrue(driver.findElements(By.cssSelector(".table-card")).size() > 0,
                "Dashboard should contain a table card");

        System.out.println("✅ Test 7 PASSED: Dashboard loaded with statistics");
    }
}
