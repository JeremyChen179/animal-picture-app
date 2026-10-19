# Setting up the project in VS Code

How to open, build, run, debug and test this Spring Boot / Maven project in Visual Studio Code on macOS. Linux and Windows are the same apart from the JDK install step.

## 1. Install a JDK (Java 21 or newer)

The project targets Java 21. Spring Boot 4 needs at least Java 17, and this project sets `java.version` to 21.

**macOS (Homebrew):**

```bash
brew install openjdk@21
```

Homebrew installs it "keg-only" (not on the PATH), so add it to your shell profile (`~/.zshrc`):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

Optionally, let macOS's system Java wrapper (`/usr/bin/java`) and other tools find it too:

```bash
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

**Windows / Linux:** install [Eclipse Temurin 21](https://adoptium.net/) and set `JAVA_HOME`.

Check it in a new terminal:

```bash
java -version     # should print: openjdk version "21..."
```

You do **not** need to install Maven: the project includes the Maven Wrapper (`mvnw`, `mvnw.cmd`), which downloads the right Maven version on first use.

## 2. Install VS Code extensions

Open the project folder in VS Code. VS Code will offer to install the **recommended extensions** listed in `.vscode/extensions.json`; click **Install All**. Or install them from the command line:

```bash
code --install-extension vscjava.vscode-java-pack       # Extension Pack for Java
code --install-extension vmware.vscode-boot-dev-pack    # Spring Boot Extension Pack
code --install-extension ms-azuretools.vscode-docker    # Docker
```

If `code` isn't found on macOS, open VS Code and run **Shell Command: Install 'code' command in PATH** from the Command Palette (⇧⌘P).

| Extension pack | What you get |
|---|---|
| Extension Pack for Java | Java language support (Red Hat), debugger, test runner, **Maven for Java**, project manager |
| Spring Boot Extension Pack | Spring Boot Dashboard (start/stop the app), `application.yml` autocomplete |
| Docker | Dockerfile / compose support, run containers from the editor |

## 3. Point VS Code at JDK 21

VS Code started from the Dock doesn't read `~/.zshrc`, so tell the Java extension where the JDK is. Open **user** settings (⇧⌘P → *Preferences: Open User Settings (JSON)*) and add:

```json
"java.configuration.runtimes": [
  {
    "name": "JavaSE-21",
    "path": "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home",
    "default": true
  }
]
```

Use your own JDK path on Windows/Linux. Check it worked: ⇧⌘P → **Java: Configure Java Runtime** should show JDK 21 for the project.

## 4. Open the project: Maven integration and library download

1. **File → Open Folder…** and choose the `animal-picture-app` folder (the one containing `pom.xml`).
2. The Java extension detects `pom.xml` and imports the project as a **Maven project**. Watch the status bar: *"Java: Importing Maven project"*. It **downloads all libraries** declared in `pom.xml` (Spring Boot, the SQLite driver, JUnit, …) from Maven Central into your local repository, `~/.m2/repository`. The first import takes a few minutes; later ones are instant.
3. When it finishes, the **Java Projects** and **Maven** views appear in the Explorer sidebar.

**The Maven config file, `pom.xml`:**

| Section | Purpose |
|---|---|
| `<parent>` `spring-boot-starter-parent` | Inherits Spring Boot's tested dependency versions and plugin settings, so most dependencies need no `<version>` |
| `<properties><java.version>` | Java version to compile for (21) |
| `<dependencies>` | Libraries: web (embedded Tomcat), REST client, JDBC, validation, the **SQLite JDBC driver** (`org.xerial:sqlite-jdbc`), and test libraries |
| `<build><plugins>` `spring-boot-maven-plugin` | Packages the app as a single **executable jar** (`java -jar`) and provides `spring-boot:run` |

**Adding a library:** add a `<dependency>` block to `pom.xml` and save. VS Code re-imports automatically (`java.configuration.updateBuildConfiguration` is `automatic` in `.vscode/settings.json`) and downloads it. To force a refresh: right-click `pom.xml` → **Reload Projects**, or ⇧⌘P → **Java: Clean Java Language Server Workspace**.

**Maven settings (`~/.m2/settings.xml`):** not needed for this project. Only create it if you need a corporate proxy or a private repository mirror.

## 5. Build, run, debug and test

| Task | In VS Code | In the terminal |
|---|---|---|
| **Run the app** | Open `AnimalPictureApplication.java` → click **Run** above `main`, or use the **Spring Boot Dashboard** → ▶ | `./mvnw spring-boot:run` |
| **Debug** | Set a breakpoint → **Debug** above `main`, or F5 with the *Run AnimalPictureApplication* config in `.vscode/launch.json` | — |
| **Run tests** | **Testing** view (flask icon) → ▶ Run All, or click ▶ next to any test | `./mvnw test` |
| **Full build** (compile + test + jar) | **Maven** view → `animal-picture-app` → Lifecycle → `verify` | `./mvnw verify` |
| **Run the built jar** | — | `java -jar target/animal-picture-app-1.0.0.jar` |
| **Docker** | Right-click `docker-compose.yml` → **Compose Up** | `docker compose up --build` |

Then open http://localhost:8080.

## Troubleshooting

| Symptom | Fix |
|---|---|
| "Unsupported class file major version" / "release 21 not supported" | VS Code is using an older JDK. Redo step 3 and reload the window |
| Red squiggles everywhere after changing `pom.xml` | Right-click `pom.xml` → **Reload Projects** |
| Libraries won't download | Check network/proxy. Run `./mvnw -U dependency:resolve` in the terminal to see the actual error |
| Port 8080 already in use | Stop the other process, or run with `PORT=8081 ./mvnw spring-boot:run` |
