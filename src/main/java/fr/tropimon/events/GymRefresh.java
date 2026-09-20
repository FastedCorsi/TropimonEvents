package fr.tropimon.events;

/** One read-only navigator request at a time; manual navigation always takes priority. */
public final class GymRefresh {
  private long next;
  private boolean pending;
  private boolean sending;

  public void reset(long now) {
    pending = false;
    sending = false;
    next = now + 5000;
  }

  public boolean due(long now) {
    return !pending && now >= next;
  }

  public void request(long now, Runnable send) {
    pending = true;
    next = now + 60000;
    sending = true;
    try {
      send.run();
    } finally {
      sending = false;
    }
  }

  public void manualCommand(String command) {
    if (!sending && (command.equals("gym") || command.startsWith("gym "))) pending = false;
  }

  public boolean consumeScreen(String name) {
    if (!pending
        || !name.equals("fr.erusel.tropimodclient.client.gui.gym.navigator.GymNavigatorScreen"))
      return false;
    pending = false;
    return true;
  }
}
