import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

// =====================================================
//                      Transaction
// =====================================================
class Transaction {

    enum Type {
        CREDIT, DEBIT, UPI;

        static Type fromString(String s) {
            if (s == null) return null;
            switch (s.trim().toLowerCase()) {
                case "c": case "credit":  return CREDIT;
                case "d": case "debit":   return DEBIT;
                case "u": case "upi":     return UPI;
                default: return null;
            }
        }
    }

    private static int nextId = 1001;
    static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    private final int id;
    private String description;
    private double amount;
    private Type type;
    private String category;
    private final LocalDateTime when;

    Transaction(String description, double amount, Type type, String category) {
        this.id          = nextId++;
        this.description = description;
        this.amount      = amount;
        this.type        = type;
        this.category    = (category == null || category.isEmpty()) ? "General" : category;
        this.when        = LocalDateTime.now();
    }

    int    getId()          { return id; }
    String getDescription() { return description; }
    double getAmount()      { return amount; }
    Type   getType()        { return type; }
    String getCategory()    { return category; }
    LocalDateTime getWhen() { return when; }

    void setDescription(String d) { this.description = d; }
    void setAmount(double a)      { this.amount = a; }
    void setType(Type t)          { this.type = t; }
    void setCategory(String c)    { this.category = c; }

    @Override
    public String toString() {
        return String.format("#%-4d  %s  %-6s  Rs. %,10.2f   %-12s  %s",
                id, when.format(FMT), type, amount, category, description);
    }
}

// =====================================================
//                        Ledger
// =====================================================
class Ledger {
    private final List<Transaction> transactions = new ArrayList<>();
    private final Map<String, Double> budgets = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    void add(Transaction t)               { transactions.add(t); }
    boolean removeById(int id)            { return transactions.removeIf(t -> t.getId() == id); }
    List<Transaction> all()               { return transactions; }

    Transaction findById(int id) {
        for (Transaction t : transactions) if (t.getId() == id) return t;
        return null;
    }

    List<Transaction> filterByType(Transaction.Type type) {
        List<Transaction> out = new ArrayList<>();
        for (Transaction t : transactions) if (t.getType() == type) out.add(t);
        return out;
    }

    List<Transaction> filterByMonth(int year, Month month) {
        List<Transaction> out = new ArrayList<>();
        for (Transaction t : transactions) {
            if (t.getWhen().getYear() == year && t.getWhen().getMonth() == month) out.add(t);
        }
        return out;
    }

    List<Transaction> search(String keyword) {
        String k = keyword.toLowerCase();
        List<Transaction> out = new ArrayList<>();
        for (Transaction t : transactions) {
            if (t.getDescription().toLowerCase().contains(k)
                    || t.getCategory().toLowerCase().contains(k)) {
                out.add(t);
            }
        }
        return out;
    }

    double totalCredit() { return sumOfType(Transaction.Type.CREDIT); }
    double totalDebit()  { return sumOfType(Transaction.Type.DEBIT); }
    double totalUpi()    { return sumOfType(Transaction.Type.UPI); }
    double balance()     { return totalCredit() - totalDebit() - totalUpi(); }

    private double sumOfType(Transaction.Type type) {
        double s = 0;
        for (Transaction t : transactions) if (t.getType() == type) s += t.getAmount();
        return s;
    }

    Map<String, Double> spendingByCategory() {
        Map<String, Double> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Transaction t : transactions) {
            if (t.getType() != Transaction.Type.CREDIT) {
                map.merge(t.getCategory(), t.getAmount(), Double::sum);
            }
        }
        return map;
    }

    /** Spending in a category for the same month as 'now'. */
    double monthSpendingForCategory(String category, YearMonth ym) {
        double s = 0;
        for (Transaction t : transactions) {
            if (t.getType() != Transaction.Type.CREDIT
                    && t.getCategory().equalsIgnoreCase(category)
                    && YearMonth.from(t.getWhen()).equals(ym)) {
                s += t.getAmount();
            }
        }
        return s;
    }

    void setBudget(String category, double limit) { budgets.put(category, limit); }
    void clearBudget(String category)             { budgets.remove(category); }
    Map<String, Double> budgets()                 { return budgets; }
    Double budgetFor(String category)             { return budgets.get(category); }
}

// =====================================================
//                         User
// =====================================================
class User {
    final String username;
    String password;
    final String email;
    final Ledger ledger = new Ledger();

    User(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email    = email;
    }
}

// =====================================================
//                    LedgerManager
// =====================================================
public class LedgerManager {

    private static final Scanner sc = new Scanner(System.in);
    private static final Map<String, User> users = new LinkedHashMap<>();
    private static User currentUser;

    // Curated, realistic spending categories shown in a numbered menu
    private static final String[] CATEGORIES = {
            "Income (salary, gifts, refunds)",
            "Food & Dining",
            "Bills & Utilities",
            "Travel & Transport",
            "Shopping",
            "Entertainment",
            "Health & Medical",
            "Other"
    };

    public static void main(String[] args) {
        System.out.println("============================================");
        System.out.println("        Ledger Management System v3.0       ");
        System.out.println("============================================");

        boolean appRunning = true;
        while (appRunning) {
            if (currentUser == null) {
                appRunning = authMenu();
            } else {
                userMenu();
            }
        }
        System.out.println("\nGoodbye!");
    }

    // =====================================================
    //                  AUTHENTICATION
    // =====================================================
    private static boolean authMenu() {
        System.out.println("\n----- Welcome -----");
        System.out.println("  1. Sign up");
        System.out.println("  2. Log in");
        System.out.println("  3. Forgot password");
        System.out.println("  4. Exit");
        int choice = readInt("Enter choice: ");
        switch (choice) {
            case 1: signup();         return true;
            case 2: login();          return true;
            case 3: forgotPassword(); return true;
            case 4: return false;
            default:
                System.out.println("  -> Invalid choice.");
                return true;
        }
    }

    private static void signup() {
        System.out.println("\n--- Create Account ---");
        System.out.println("Username rules: 4-15 chars, must start with a letter,");
        System.out.println("                only letters, digits or underscore (no spaces).");
        String username = readUsername();

        String email;
        while (true) {
            System.out.print("Email: ");
            email = sc.nextLine().trim().toLowerCase();
            if (email.matches("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$")) break;
            System.out.println("  -> Invalid email format.");
        }

        System.out.println("Password rules: 8-15 chars, must contain a letter, a digit");
        System.out.println("                and a symbol (!@#$%^&*...), no spaces.");
        String password = readStrongPassword();

        users.put(username.toLowerCase(), new User(username, password, email));
        System.out.println("  -> Account created. You can now log in.");
    }

    private static void login() {
        System.out.println("\n--- Log In ---");
        for (int attempts = 0; attempts < 3; attempts++) {
            System.out.print("Username: ");
            String u = sc.nextLine().trim().toLowerCase();
            System.out.print("Password: ");
            String p = sc.nextLine();
            User user = users.get(u);
            if (user != null && user.password.equals(p)) {
                currentUser = user;
                System.out.println("  -> Welcome, " + user.username + "!");
                return;
            }
            System.out.println("  -> Invalid credentials. Attempts left: " + (2 - attempts));
        }
        System.out.println("  -> Login failed.");
    }

    private static void logout() {
        System.out.println("  -> Logged out " + currentUser.username + ".");
        currentUser = null;
    }

    private static void forgotPassword() {
        System.out.println("\n--- Forgot Password ---");
        System.out.print("Username: ");
        String u = sc.nextLine().trim().toLowerCase();
        User user = users.get(u);
        if (user == null) {
            System.out.println("  -> No account found with that username.");
            return;
        }
        boolean verified = false;
        for (int attempts = 0; attempts < 3; attempts++) {
            System.out.print("Registered email: ");
            String email = sc.nextLine().trim().toLowerCase();
            if (email.equals(user.email)) { verified = true; break; }
            System.out.println("  -> Email does not match. Attempts left: " + (2 - attempts));
        }
        if (!verified) {
            System.out.println("  -> Verification failed. Returning to menu.");
            return;
        }
        System.out.println("  -> Identity confirmed. Set a new password.");
        System.out.println("Password rules: 8-15 chars, must contain a letter, a digit");
        System.out.println("                and a symbol (!@#$%^&*...), no spaces.");
        String newPassword = readStrongPassword();
        if (newPassword.equals(user.password)) {
            System.out.println("  -> New password cannot be the same as the old one.");
            return;
        }
        user.password = newPassword;
        System.out.println("  -> Password updated successfully. You can now log in.");
    }

    // =====================================================
    //                   USER MAIN MENU
    // =====================================================
    private static void userMenu() {
        printMenu();
        int choice = readInt("Enter choice: ");
        switch (choice) {
            case 1:  addTransaction();        break;
            case 2:  editTransaction();       break;
            case 3:  removeTransaction();     break;
            case 4:  viewAll();               break;
            case 5:  viewByType();            break;
            case 6:  viewByMonth();           break;
            case 7:  search();                break;
            case 8:  sortAndTopN();           break;
            case 9:  viewBalance();           break;
            case 10: viewCategoryBreakdown(); break;
            case 11: manageBudgets();         break;
            case 12: exportStatement();       break;
            case 13: changePassword();        break;
            case 14: logout();                break;
            default: System.out.println("  -> Invalid choice. Pick 1-14.");
        }
    }

    private static void printMenu() {
        System.out.println("\n--------------------------------------------");
        System.out.println(" Logged in as: " + currentUser.username);
        System.out.println("--------------------------------------------");
        System.out.println("  1. Add transaction");
        System.out.println("  2. Edit transaction");
        System.out.println("  3. Remove transaction");
        System.out.println("  4. View all transactions");
        System.out.println("  5. View by type (credit / debit / UPI)");
        System.out.println("  6. View by month");
        System.out.println("  7. Search transactions");
        System.out.println("  8. Sort & top-N view");
        System.out.println("  9. Balance summary");
        System.out.println(" 10. Spending by category");
        System.out.println(" 11. Manage budgets");
        System.out.println(" 12. Export statement");
        System.out.println(" 13. Change password");
        System.out.println(" 14. Logout");
        System.out.println("--------------------------------------------");
    }

    private static void changePassword() {
        System.out.println("\n--- Change Password ---");
        boolean verified = false;
        for (int attempts = 0; attempts < 3; attempts++) {
            System.out.print("Current password: ");
            String p = sc.nextLine();
            if (p.equals(currentUser.password)) { verified = true; break; }
            System.out.println("  -> Incorrect. Attempts left: " + (2 - attempts));
        }
        if (!verified) {
            System.out.println("  -> Verification failed. Returning to menu.");
            return;
        }
        System.out.println("Password rules: 8-15 chars, must contain a letter, a digit");
        System.out.println("                and a symbol (!@#$%^&*...), no spaces.");
        String newPassword = readStrongPassword();
        if (newPassword.equals(currentUser.password)) {
            System.out.println("  -> New password cannot be the same as the current one.");
            return;
        }
        currentUser.password = newPassword;
        System.out.println("  -> Password changed successfully.");
    }

    // =====================================================
    //                ADD / EDIT / REMOVE
    // =====================================================
    private static void addTransaction() {
        System.out.println("\n--- Add Transaction ---");
        String description    = readAlphaNumDesc("Description (letters/digits/spaces): ");
        double amount         = readPositiveDouble("Amount (positive number): ");
        Transaction.Type type = readType("Type (credit / debit / upi): ");
        String category       = pickCategory();

        if (type == Transaction.Type.UPI) {
            System.out.println("  Note: UPI is treated as money out (like a debit), but tracked");
            System.out.println("        separately so you can see how much you spent via UPI.");
            System.out.print("UPI reference (e.g. shop@okaxis, leave blank to skip): ");
            String ref = sc.nextLine().trim();
            if (!ref.isEmpty()) description = description + " [UPI: " + ref + "]";
        }

        Transaction t = new Transaction(description, amount, type, category);
        currentUser.ledger.add(t);
        System.out.println("\n  -> Added " + t);

        if (type != Transaction.Type.CREDIT) {
            String cat = t.getCategory();
            Double limit = currentUser.ledger.budgetFor(cat);
            if (limit != null) {
                YearMonth ym = YearMonth.from(t.getWhen());
                double spent = currentUser.ledger.monthSpendingForCategory(cat, ym);
                System.out.printf("  Budget for %s this month: Rs. %,.2f / Rs. %,.2f%n",
                        cat, spent, limit);
                if (spent > limit) {
                    System.out.printf("  !! WARNING: You have exceeded your %s budget by Rs. %,.2f%n",
                            cat, spent - limit);
                } else if (spent > limit * 0.8) {
                    System.out.println("  ! NOTE: You are within 20% of your budget for " + cat + ".");
                }
            }
        }
    }

    private static void editTransaction() {
        if (currentUser.ledger.all().isEmpty()) {
            System.out.println("\nNo transactions to edit.");
            return;
        }
        viewAll();
        int id = readInt("Enter transaction ID to edit: ");
        Transaction t = currentUser.ledger.findById(id);
        if (t == null) {
            System.out.println("  -> No transaction found with ID " + id + ".");
            return;
        }
        System.out.println("\nEditing: " + t);
        System.out.println("  1. Description   2. Amount   3. Type   4. Category   5. Cancel");
        int field = readInt("Which field to edit? ");
        switch (field) {
            case 1:
                t.setDescription(readAlphaNumDesc("New description (letters/digits/spaces): "));
                break;
            case 2:
                t.setAmount(readPositiveDouble("New amount: "));
                break;
            case 3:
                t.setType(readType("New type (credit / debit / upi): "));
                break;
            case 4:
                t.setCategory(pickCategory());
                break;
            case 5: System.out.println("  -> Cancelled."); return;
            default: System.out.println("  -> Invalid field."); return;
        }
        System.out.println("  -> Updated: " + t);
    }

    private static void removeTransaction() {
        if (currentUser.ledger.all().isEmpty()) {
            System.out.println("\nNo transactions to remove.");
            return;
        }
        viewAll();
        int id = readInt("Enter transaction ID to remove: ");
        if (currentUser.ledger.removeById(id)) {
            System.out.println("  -> Transaction #" + id + " removed.");
        } else {
            System.out.println("  -> No transaction found with ID " + id + ".");
        }
    }

    // =====================================================
    //                       VIEWS
    // =====================================================
    private static void viewAll() {
        List<Transaction> list = currentUser.ledger.all();
        if (list.isEmpty()) {
            System.out.println("\nNo transactions yet.");
            return;
        }
        printTable(list, "All Transactions");
    }

    private static void viewByType() {
        Transaction.Type t = readType("Filter type (credit / debit / upi): ");
        List<Transaction> list = currentUser.ledger.filterByType(t);
        if (list.isEmpty()) {
            System.out.println("\nNo " + t + " transactions found.");
            return;
        }
        printTable(list, t + " Transactions");
    }

    private static void viewByMonth() {
        System.out.println("\n--- View by Month ---");
        int year  = readYear("Year (1900-2100): ");
        int month;
        while (true) {
            month = readInt("Month (1-12): ");
            if (month >= 1 && month <= 12) break;
            System.out.println("  -> Must be 1-12.");
        }
        Month m = Month.of(month);
        List<Transaction> list = currentUser.ledger.filterByMonth(year, m);
        String label = m.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
        if (list.isEmpty()) {
            System.out.println("\nNo transactions in " + label + ".");
            return;
        }
        printTable(list, "Transactions in " + label);
        double credit = 0, debit = 0, upi = 0;
        for (Transaction tt : list) {
            if (tt.getType() == Transaction.Type.CREDIT) credit += tt.getAmount();
            else if (tt.getType() == Transaction.Type.DEBIT) debit += tt.getAmount();
            else upi += tt.getAmount();
        }
        System.out.println("\nMonthly summary for " + label + ":");
        System.out.printf("  Credits : Rs. %,12.2f%n", credit);
        System.out.printf("  Debits  : Rs. %,12.2f%n", debit);
        System.out.printf("  UPI     : Rs. %,12.2f%n", upi);
        System.out.printf("  Net     : Rs. %,12.2f%n", credit - debit - upi);
    }

    private static void search() {
        System.out.print("Enter keyword (matches description or category): ");
        String k = sc.nextLine().trim();
        if (k.isEmpty()) {
            System.out.println("  -> Keyword cannot be empty.");
            return;
        }
        List<Transaction> list = currentUser.ledger.search(k);
        if (list.isEmpty()) {
            System.out.println("\nNo matching transactions found.");
            return;
        }
        printTable(list, "Search results for \"" + k + "\"");
    }

    // =====================================================
    //                  SORT & TOP-N
    // =====================================================
    private static void sortAndTopN() {
        if (currentUser.ledger.all().isEmpty()) {
            System.out.println("\nNo transactions yet.");
            return;
        }
        System.out.println("\n--- Sort & Top-N ---");
        System.out.println("  1. Sort by date (newest first)");
        System.out.println("  2. Sort by date (oldest first)");
        System.out.println("  3. Sort by amount (largest first)");
        System.out.println("  4. Sort by amount (smallest first)");
        System.out.println("  5. Top N largest expenses (debit + UPI)");
        int choice = readInt("Enter choice: ");

        List<Transaction> list = new ArrayList<>(currentUser.ledger.all());
        switch (choice) {
            case 1: list.sort((a, b) -> b.getWhen().compareTo(a.getWhen())); break;
            case 2: list.sort(Comparator.comparing(Transaction::getWhen));   break;
            case 3: list.sort((a, b) -> Double.compare(b.getAmount(), a.getAmount())); break;
            case 4: list.sort(Comparator.comparingDouble(Transaction::getAmount)); break;
            case 5:
                int n = readInt("How many to show? ");
                List<Transaction> expenses = new ArrayList<>();
                for (Transaction t : currentUser.ledger.all()) {
                    if (t.getType() != Transaction.Type.CREDIT) expenses.add(t);
                }
                expenses.sort((a, b) -> Double.compare(b.getAmount(), a.getAmount()));
                if (expenses.size() > n) expenses = expenses.subList(0, n);
                if (expenses.isEmpty()) {
                    System.out.println("  -> No expenses recorded.");
                    return;
                }
                printTable(expenses, "Top " + n + " expenses");
                return;
            default:
                System.out.println("  -> Invalid choice.");
                return;
        }
        printTable(list, "Sorted transactions");
    }

    // =====================================================
    //                     SUMMARIES
    // =====================================================
    private static void viewBalance() {
        Ledger L = currentUser.ledger;
        System.out.println("\n--- Balance Summary ---");
        System.out.printf("Total credits : Rs. %,12.2f%n", L.totalCredit());
        System.out.printf("Total debits  : Rs. %,12.2f%n", L.totalDebit());
        System.out.printf("Total UPI     : Rs. %,12.2f%n", L.totalUpi());
        System.out.println("-----------------------------------");
        double b = L.balance();
        System.out.printf("Net balance   : Rs. %,12.2f  %s%n", b, b < 0 ? "(in deficit)" : "");
    }

    private static void viewCategoryBreakdown() {
        Map<String, Double> map = currentUser.ledger.spendingByCategory();
        if (map.isEmpty()) {
            System.out.println("\nNo spending recorded yet.");
            return;
        }
        System.out.println("\n--- Spending by Category ---");
        double total = 0;
        for (Map.Entry<String, Double> e : map.entrySet()) {
            System.out.printf("  %-15s Rs. %,12.2f%n", e.getKey(), e.getValue());
            total += e.getValue();
        }
        System.out.println("-----------------------------------");
        System.out.printf("  %-15s Rs. %,12.2f%n", "TOTAL SPENT", total);
    }

    // =====================================================
    //                       BUDGETS
    // =====================================================
    private static void manageBudgets() {
        System.out.println("\n--- Budgets ---");
        System.out.println("  1. View current budgets");
        System.out.println("  2. Set / update a budget");
        System.out.println("  3. Remove a budget");
        System.out.println("  4. Back");
        int choice = readInt("Enter choice: ");
        switch (choice) {
            case 1: viewBudgets(); break;
            case 2: {
                String c = pickCategory();
                double limit = readPositiveDouble("Monthly limit (Rs.): ");
                currentUser.ledger.setBudget(c, limit);
                System.out.println("  -> Budget set: " + c + " = Rs. " + String.format("%,.2f", limit));
                break;
            }
            case 3: {
                Map<String, Double> b = currentUser.ledger.budgets();
                if (b.isEmpty()) { System.out.println("  -> No budgets to remove."); return; }
                List<String> keys = new ArrayList<>(b.keySet());
                System.out.println("Pick a budget to remove:");
                for (int i = 0; i < keys.size(); i++) {
                    System.out.printf("  %d. %s (Rs. %,.2f)%n", i + 1, keys.get(i), b.get(keys.get(i)));
                }
                int idx = readInt("Enter choice: ");
                if (idx < 1 || idx > keys.size()) { System.out.println("  -> Invalid choice."); return; }
                String r = keys.get(idx - 1);
                currentUser.ledger.clearBudget(r);
                System.out.println("  -> Removed budget for " + r + ".");
                break;
            }
            case 4: return;
            default: System.out.println("  -> Invalid choice.");
        }
    }

    private static void viewBudgets() {
        Map<String, Double> b = currentUser.ledger.budgets();
        if (b.isEmpty()) {
            System.out.println("  -> No budgets set yet.");
            return;
        }
        YearMonth ym = YearMonth.now();
        System.out.println("\nBudgets for " + ym + ":");
        System.out.printf("  %-15s %12s %12s %12s%n", "Category", "Limit", "Spent", "Remaining");
        System.out.println("  ----------------------------------------------------------");
        for (Map.Entry<String, Double> e : b.entrySet()) {
            double spent = currentUser.ledger.monthSpendingForCategory(e.getKey(), ym);
            double left  = e.getValue() - spent;
            String flag  = left < 0 ? "  OVER" : (spent > e.getValue() * 0.8 ? "  near" : "");
            System.out.printf("  %-15s %,12.2f %,12.2f %,12.2f%s%n",
                    e.getKey(), e.getValue(), spent, left, flag);
        }
    }

    // =====================================================
    //                  EXPORT STATEMENT
    // =====================================================
    private static void exportStatement() {
        if (currentUser.ledger.all().isEmpty()) {
            System.out.println("\nNo transactions to export.");
            return;
        }
        System.out.println("\n--- Export Statement ---");
        System.out.println("  1. Print to screen");
        System.out.println("  2. Save to a file");
        int choice = readInt("Enter choice: ");
        StringBuilder sb = buildStatement();
        if (choice == 1) {
            System.out.println();
            System.out.println(sb);
        } else if (choice == 2) {
            String filename = "statement_" + currentUser.username + "_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    + ".txt";
            try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
                pw.print(sb);
                System.out.println("  -> Saved to " + filename);
            } catch (IOException e) {
                System.out.println("  -> Failed to save file: " + e.getMessage());
            }
        } else {
            System.out.println("  -> Invalid choice.");
        }
    }

    private static StringBuilder buildStatement() {
        Ledger L = currentUser.ledger;
        StringBuilder sb = new StringBuilder();
        sb.append("=========================================================================\n");
        sb.append("                 LEDGER STATEMENT\n");
        sb.append("  User    : ").append(currentUser.username).append("\n");
        sb.append("  Email   : ").append(currentUser.email).append("\n");
        sb.append("  Issued  : ").append(LocalDateTime.now().format(Transaction.FMT)).append("\n");
        sb.append("=========================================================================\n");
        sb.append(String.format("%-6s %-18s %-7s %12s   %-12s  %s%n",
                "ID", "Date", "Type", "Amount", "Category", "Description"));
        sb.append("-------------------------------------------------------------------------\n");

        List<Transaction> sorted = new ArrayList<>(L.all());
        sorted.sort(Comparator.comparing(Transaction::getWhen));

        double running = 0;
        for (Transaction t : sorted) {
            sb.append(t).append("\n");
            running += (t.getType() == Transaction.Type.CREDIT)
                    ? t.getAmount() : -t.getAmount();
            sb.append(String.format("        running balance: Rs. %,.2f%n", running));
        }
        sb.append("-------------------------------------------------------------------------\n");
        sb.append(String.format("Total credits : Rs. %,12.2f%n", L.totalCredit()));
        sb.append(String.format("Total debits  : Rs. %,12.2f%n", L.totalDebit()));
        sb.append(String.format("Total UPI     : Rs. %,12.2f%n", L.totalUpi()));
        sb.append("-------------------------------------------------------------------------\n");
        sb.append(String.format("CLOSING BALANCE: Rs. %,.2f%n", L.balance()));
        sb.append("=========================================================================\n");
        return sb;
    }

    // =====================================================
    //                  PRINTING HELPERS
    // =====================================================
    private static void printTable(List<Transaction> list, String title) {
        System.out.println("\n" + title);
        System.out.println("------------------------------------------------------------------------------------------------");
        System.out.printf("%-6s %-18s %-7s %12s   %-12s  %s%n",
                "ID", "Date", "Type", "Amount", "Category", "Description");
        System.out.println("------------------------------------------------------------------------------------------------");
        for (Transaction t : list) System.out.println(t);
        System.out.println("------------------------------------------------------------------------------------------------");
        System.out.println("Total entries: " + list.size());
    }

    // =====================================================
    //                    INPUT HELPERS
    // =====================================================
    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = sc.nextLine().trim();
            try { return Integer.parseInt(line); }
            catch (NumberFormatException e) {
                System.out.println("  -> Please enter a valid integer.");
            }
        }
    }

    private static double readPositiveDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = sc.nextLine().trim();
            try {
                double v = Double.parseDouble(line);
                if (v > 0) return v;
                System.out.println("  -> Must be greater than 0.");
            } catch (NumberFormatException e) {
                System.out.println("  -> Please enter a valid number.");
            }
        }
    }

    private static String readNonEmpty(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = sc.nextLine().trim();
            if (!s.isEmpty()) return s;
            System.out.println("  -> Cannot be empty.");
        }
    }

    private static Transaction.Type readType(String prompt) {
        while (true) {
            System.out.print(prompt);
            Transaction.Type t = Transaction.Type.fromString(sc.nextLine());
            if (t != null) return t;
            System.out.println("  -> Invalid type. Use credit, debit, or upi.");
        }
    }

    /** Username: 4-15 chars, starts with letter, only letters/digits/underscore, unique. */
    private static String readUsername() {
        while (true) {
            System.out.print("Choose a username: ");
            String u = sc.nextLine().trim();
            if (!u.matches("^[A-Za-z][A-Za-z0-9_]{3,14}$")) {
                System.out.println("  -> Username must be 4-15 chars, start with a letter,");
                System.out.println("     and only contain letters, digits or underscore.");
                continue;
            }
            if (users.containsKey(u.toLowerCase())) {
                System.out.println("  -> Username already taken.");
                continue;
            }
            return u;
        }
    }

    /** Password: 8-15 chars, must contain a letter, a digit and a symbol; no spaces. */
    private static String readStrongPassword() {
        while (true) {
            System.out.print("Choose a password: ");
            String p = sc.nextLine();
            if (p.length() < 8 || p.length() > 15) {
                System.out.println("  -> Password must be 8 to 15 characters long.");
                continue;
            }
            if (p.contains(" ")) {
                System.out.println("  -> Password cannot contain spaces.");
                continue;
            }
            boolean hasLetter = false, hasDigit = false, hasSymbol = false;
            for (char ch : p.toCharArray()) {
                if (Character.isLetter(ch))      hasLetter = true;
                else if (Character.isDigit(ch))  hasDigit  = true;
                else                             hasSymbol = true;
            }
            if (!hasLetter || !hasDigit || !hasSymbol) {
                System.out.println("  -> Password must contain a letter, a digit AND a symbol.");
                continue;
            }
            return p;
        }
    }

    /** Description: 2-60 chars, only letters, digits and spaces; cannot be all digits. */
    private static String readAlphaNumDesc(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = sc.nextLine().trim().replaceAll("\\s+", " ");
            if (s.length() < 2 || s.length() > 60) {
                System.out.println("  -> Description must be 2 to 60 characters.");
                continue;
            }
            if (!s.matches("[A-Za-z0-9 ]+")) {
                System.out.println("  -> Only letters, digits and spaces are allowed.");
                continue;
            }
            if (!s.matches(".*[A-Za-z].*")) {
                System.out.println("  -> Description must contain at least one letter.");
                continue;
            }
            return s;
        }
    }

    /** Year: integer between 1900 and 2100 inclusive. */
    private static int readYear(String prompt) {
        while (true) {
            int y = readInt(prompt);
            if (y >= 1900 && y <= 2100) return y;
            System.out.println("  -> Year must be between 1900 and 2100.");
        }
    }

    /** Show curated category list and return the picked one. */
    private static String pickCategory() {
        System.out.println("Choose a category:");
        for (int i = 0; i < CATEGORIES.length; i++) {
            System.out.printf("  %d. %s%n", i + 1, CATEGORIES[i]);
        }
        while (true) {
            int n = readInt("Enter choice (1-" + CATEGORIES.length + "): ");
            if (n >= 1 && n <= CATEGORIES.length) return CATEGORIES[n - 1];
            System.out.println("  -> Pick a number from the list.");
        }
    }
}