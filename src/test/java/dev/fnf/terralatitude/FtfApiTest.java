package dev.fnf.terralatitude;

public final class FtfApiTest {
    public static void main(String[] args) {
        boolean missing = args.length == 1 && args[0].equals("missing");
        try {
            FtfApi.requireSupported();
            if (missing) throw new AssertionError("Unsupported FTF passed the startup API check");
        } catch (IllegalStateException error) {
            if (!missing) throw error;
            if (!(error.getCause() instanceof NoSuchMethodException)
                    || !error.getMessage().contains("PR #301")) {
                throw new AssertionError("Unsupported FTF did not give the intended API error", error);
            }
        }
        System.out.println("FtfApiTest passed (" + (missing ? "missing raw API rejected clearly" : "PR raw API accepted") + ")");
    }
}
