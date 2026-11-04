package slatefinder;

/** A server that dies as it starts, for the test of what the app says then. */
public final class CrashStart {
    public static void main(String[] a) {
        System.out.println("[main/ERROR]: Failed to start the minecraft server");
        System.exit(1);
    }
}
