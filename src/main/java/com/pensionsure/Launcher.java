package com.pensionsure;

/**
 * Standard launcher entry point for shaded fat JAR distribution.
 *
 * A class that extends {@code javafx.application.Application} cannot be launched
 * directly via {@code java -jar} when packaged as a fat JAR because the JVM
 * launcher checks for JavaFX runtime components before loading the class.
 * Delegating from this standalone class bypasses that module check.
 */
public class Launcher {

    public static void main(String[] args) {
        Main.main(args);
    }
}
