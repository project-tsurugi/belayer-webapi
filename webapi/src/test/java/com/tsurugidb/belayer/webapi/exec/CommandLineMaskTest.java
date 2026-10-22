package com.tsurugidb.belayer.webapi.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The command line written to the debug log does not contain the token (BW-44).
 */
public class CommandLineMaskTest {

    @Test
    public void test_mask_token() {
        String[] args = "tgctl_w shutdown --monitor /tmp/m.log --conf \"tsurugi.ini\" --timeout 1 --auth-token SECRET"
                .split(" ");

        assertEquals(Arrays.asList("tgctl_w", "shutdown", "--monitor", "/tmp/m.log", "--conf", "\"tsurugi.ini\"",
                "--timeout", "1", "--auth-token", "********"), CommandLineMask.mask(args));
        // the arguments passed to the process are not changed
        assertEquals("SECRET", args[args.length - 1]);
    }

    @Test
    public void test_mask_token_in_the_middle() {
        List<String> args = Arrays.asList("tgha_w", "database", "apply", "--auth-token", "SECRET", "--from", "x",
                "--auto");

        assertEquals(Arrays.asList("tgha_w", "database", "apply", "--auth-token", "********", "--from", "x",
                "--auto"), CommandLineMask.mask(args));
    }

    @Test
    public void test_mask_token_with_equals() {
        assertEquals(Arrays.asList("cmd", "--auth-token=********"),
                CommandLineMask.mask(new String[] { "cmd", "--auth-token=SECRET" }));
    }

    @Test
    public void test_no_token() {
        List<String> args = Arrays.asList("cmd", "--monitor", "/tmp/m.log");
        assertEquals(args, CommandLineMask.mask(args));
    }

    @Test
    public void test_option_at_the_end() {
        List<String> args = Arrays.asList("cmd", "--auth-token");
        assertEquals(args, CommandLineMask.mask(args));
    }

    @Test
    public void test_null_and_empty() {
        List<String> args = new ArrayList<>();
        args.add("cmd");
        args.add(null);
        args.add("--auth-token");
        args.add("");
        assertEquals(Arrays.asList("cmd", null, "--auth-token", "********"), CommandLineMask.mask(args));
    }

    @Test
    public void test_all_templates() {
        // the templates of application-common.properties after String.format(), as the Execs build them
        String[] lines = {
            "tgctl_w start --monitor m --conf \"c\" --timeout 1 --auth-token SECRET --mode standalone",
            "tgha_w mode switch standalone --monitor m --conf \"c\" --timeout 1 --auth-token SECRET",
            "tgha_w database apply --monitor m --conf \"c\" --auth-token SECRET --from \"f\" --auto",
            "tgctl_w shutdown --monitor m --conf \"c\" --timeout 1 --auth-token SECRET",
            "tgctl_w quiesce --monitor m --conf \"c\" --label \"l\" --auth-token SECRET",
            "tgctl_w backup create \"d\" --monitor m --conf \"c\" --force --auth-token SECRET",
            "tgctl_w restore backup \"d\" --monitor m --conf \"c\" --force --auth-token SECRET",
            "tgctl_w session show :1 --monitor m --conf \"c\" --auth-token SECRET",
            "tgctl_w session set :1 n v --monitor m --conf \"c\" --auth-token SECRET",
            "tgctl_w session shutdown :1 --monitor m --conf \"c\" --auth-token SECRET",
        };
        for (String line : lines) {
            String masked = String.join(" ", CommandLineMask.mask(line.split(" ")));
            assertEquals(line.replace("SECRET", "********"), masked);
        }
    }
}
