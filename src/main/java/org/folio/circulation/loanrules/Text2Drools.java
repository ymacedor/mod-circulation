package org.folio.circulation.loanrules;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.StringEscapeUtils;
import org.folio.circulation.loanrules.LoanRulesParser.CriteriumContext;
import org.folio.circulation.loanrules.LoanRulesParser.CriteriumPriorityContext;
import org.folio.circulation.loanrules.LoanRulesParser.DedentContext;
import org.folio.circulation.loanrules.LoanRulesParser.DefaultPrioritiesContext;
import org.folio.circulation.loanrules.LoanRulesParser.ExprContext;
import org.folio.circulation.loanrules.LoanRulesParser.FallbackpolicyContext;
import org.folio.circulation.loanrules.LoanRulesParser.IndentContext;
import org.folio.circulation.loanrules.LoanRulesParser.LastLinePrioritiesContext;
import org.folio.circulation.loanrules.LoanRulesParser.LinePriorityContext;
import org.folio.circulation.loanrules.LoanRulesParser.LoanRulesFileContext;
import org.folio.circulation.loanrules.LoanRulesParser.PolicyContext;
import org.folio.circulation.loanrules.LoanRulesParser.SevenCriteriumLettersContext;
import org.folio.circulation.loanrules.LoanRulesParser.ThreePrioritiesContext;
import org.folio.circulation.loanrules.LoanRulesParser.TwoPrioritiesContext;

/**
 * Convert a loan rules text in FOLIO format into a drools rules text.
 */
public class Text2Drools extends LoanRulesBaseListener {
  @SuppressWarnings("squid:CommentedOutCodeLine")  // Example code is allowed
  /* Example drools file:

  package loanrules
  import org.folio.circulation.loanrules.*
  global LoanPolicy loanPolicy
  global java.lang.Integer lineNumber

  // fallback-policy
  rule "line 1"
    salience 0
    when
    then
      match.loanPolicyId = "ffffffff-2222-4b5e-a7bd-064b8d177231";
      match.lineNumber = 1;
      drools.halt();
  end

  rule "line 2"
    salience 1
    when
      ItemType(id == "aaaaaaaa-1111-4b5e-a7bd-064b8d177231")
    then
      match.loanPolicyId = "ffffffff-3333-4b5e-a7bd-064b8d177231";
      match.lineNumber = 2;
      drools.halt();
  end

  rule "line 3"
    salience 2
    when
      ItemType(id == "aaaaaaaa-1111-4b5e-a7bd-064b8d177231")
      PatronGroup(id == "cccccccc-1111-4b5e-a7bd-064b8d177231")
    then
      match.loanPolicyId = "ffffffff-4444-4b5e-a7bd-064b8d177231";
      match.lineNumber = 3;
      drools.halt();
  end

  */

  private StringBuilder drools = new StringBuilder(
      "package loanrules\n" +
      "import org.folio.circulation.loanrules.*\n" +
      "global Match match\n" +
      "\n"
      );

  private static class Matcher {
    int indentation;
    Set<String> criteriaUsed = new HashSet<>(4);
    int maxCriteriumPriority;
    StringBuilder drools;
    public Matcher(int indentation, Set<String> criteriaUsed, int maxCriteriumPriority, StringBuilder drools) {
      this.indentation = indentation;
      this.criteriaUsed.addAll(criteriaUsed);
      this.maxCriteriumPriority = maxCriteriumPriority;
      this.drools = drools;
    }
  }
  private LinkedList<Matcher> stack = new LinkedList<>();
  private static Matcher defaultMatcher = new Matcher(0, Collections.emptySet(), 0, null);

  private int indentation = 0;

  private enum PriorityType {
    NONE,
    FIRST_LINE,
    LAST_LINE,
    NUMBER_OF_CRITERIA,
    CRITERIUM;
    public static PriorityType getPriorityType(String type) {
      switch (type) {
      case "":                   return NONE;
      case "first-line":         return FIRST_LINE;
      case "last-line":          return LAST_LINE;
      case "number-of-criteria": return NUMBER_OF_CRITERIA;
      case "criterium":          return CRITERIUM;
      default: throw new IllegalArgumentException("Unknown type name: " + type);
      }
    }
  }
  private PriorityType [] priority =
    { PriorityType.NONE, PriorityType.NONE, PriorityType.FIRST_LINE };

  private Map<String,Integer> criteriumPriority = new HashMap<>(7);

  /** Private constructor to be invoked from convert(String) only. */
  private Text2Drools() {
  }

  /**
   * Convert loan rules from FOLIO text format into a Drools file.
   * @param text String with a loan rules file in FOLIO syntax.
   * @return Drools file
   */
  public static String convert(String text) {
    Text2Drools text2drools = new Text2Drools();

    CharStream input = CharStreams.fromString(text);
    LoanRulesLexer lexer = new LoanRulesLexer(input);
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    LoanRulesParser parser = new LoanRulesParser(tokens);
    parser.removeErrorListeners(); // remove ConsoleErrorListener
    parser.addErrorListener(new ErrorListener());
    LoanRulesFileContext entryPoint = parser.loanRulesFile();
    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(text2drools, entryPoint);

    return text2drools.drools.toString();
  }

  /**
   * Pop all matchers whose indentation is >= the current indentation.
   */
  private void popObsoleteMatchers() {
    while (! stack.isEmpty()) {
      Matcher matcher = stack.peek();
      if (matcher.indentation < indentation) {
        return;
      }
      stack.pop();
    }
  }

  private void setIndentation(TerminalNode node) {
    // INDENT10 -> 10
    // DEDENT2 -> 2
    indentation = Integer.parseInt(node.getText().substring(6));
  }

  @Override
  public void exitIndent(IndentContext indent) {
    setIndentation(indent.INDENT());
  }

  @Override
  public void exitDedent(DedentContext dedent) {
    setIndentation(dedent.DEDENT());
  }

  @Override
  public void exitSevenCriteriumLetters(SevenCriteriumLettersContext letters) {
    int size = letters.CRITERIUM_LETTER().size();
    if (size != 7) {
      Token token = letters.getStart();
      String message = size < 7 ? "7 letters expected, found only " + size
                                : "Only 7 letters expected, found " + size;
      throw new LoanRulesException(message,
          token.getLine(), token.getCharPositionInLine() + 1);
    }

    for (int i=0; i<7; i++) {
      String letter = letters.CRITERIUM_LETTER(i).getText();
      if (criteriumPriority.put(letter, 7 - i) != null) {
        Token token = letters.CRITERIUM_LETTER(i).getSymbol();
        throw new LoanRulesException("Duplicate letter " + letter,
            token.getLine(), token.getCharPositionInLine() + 1);
      }
    }
  }

  private PriorityType getType(CriteriumPriorityContext ctx) {
    if (ctx.sevenCriteriumLetters() != null) {
      return PriorityType.CRITERIUM;
    }
    return PriorityType.NUMBER_OF_CRITERIA;
  }

  private PriorityType getType(LinePriorityContext ctx) {
    return PriorityType.getPriorityType(ctx.getText());
  }

  @Override
  public void exitLastLinePriorities(LastLinePrioritiesContext ctx) {
    priority[0] = PriorityType.NONE;
    priority[1] = PriorityType.NONE;
    priority[2] = PriorityType.LAST_LINE;
  }

  @Override
  public void exitTwoPriorities(TwoPrioritiesContext ctx) {
    priority[0] = PriorityType.NONE;
    priority[1] = getType(ctx.criteriumPriority());
    priority[2] = getType(ctx.linePriority());
  }

  @Override
  public void exitThreePriorities(ThreePrioritiesContext ctx) {
    priority[0] = getType(ctx.criteriumPriority(0));
    priority[1] = getType(ctx.criteriumPriority(1));
    priority[2] = getType(ctx.linePriority());

    if (priority[0].equals(priority[1])) {
      Token token = ctx.criteriumPriority(1).getStart();
      throw new LoanRulesException("Duplicate priority type",
          token.getLine(), token.getCharPositionInLine() + 1);
    }
  }

  @Override
  public void exitDefaultPriorities(DefaultPrioritiesContext ctx) {
    priority[0] = PriorityType.CRITERIUM;
    priority[1] = PriorityType.NUMBER_OF_CRITERIA;
    priority[2] = PriorityType.LAST_LINE;
  }

  @Override
  public void exitFallbackpolicy(FallbackpolicyContext fallbackpolicy) {
    popObsoleteMatchers();
    generateRule(fallbackpolicy.policy());
  }

  @Override
  public void exitExpr(ExprContext expr) {
    popObsoleteMatchers();

    Matcher previousMatcher = stack.peek();
    if (previousMatcher == null) {
      previousMatcher = defaultMatcher;
    }
    StringBuilder s = new StringBuilder();
    Matcher matcher = new Matcher(indentation,
        previousMatcher.criteriaUsed, previousMatcher.maxCriteriumPriority, s);

    for (CriteriumContext criteriumContext : expr.criterium()) {
      addCriterium(criteriumContext, matcher);
    }
    stack.push(matcher);

    generateRule(expr.policy());
  }

  private void generateRule(PolicyContext policy) {
    if (policy == null) {
      return;
    }

    int line = policy.getStart().getLine();
    drools.append("rule \"line ").append(line).append("\"\n");
    drools.append("  salience ").append(getSalience(line)).append("\n");
    drools.append("  when\n");
    stack.descendingIterator().forEachRemaining(matcher -> drools.append(matcher.drools));
    drools.append("  then\n");
    drools.append("    match.loanPolicyId = ");
    appendQuotedString(drools, policy.NAME(0).getText());
    drools.append(     ";\n");
    drools.append("    match.lineNumber = ").append(line).append(";\n");
    drools.append("    drools.halt();\n");
    drools.append("end\n\n");
  }

  private int getSalience(int line) {
    int salience = line;
    if (priority[2] == PriorityType.FIRST_LINE) {
      salience = 10000000 - line;
    }

    Matcher matcher = stack.peek();
    salience += priority(matcher, priority[1]) *  10000000;
    salience += priority(matcher, priority[0]) * 100000000;

    return salience;
  }

  private static int priority(Matcher matcher, PriorityType type) {
    if (matcher == null) {  // fallback-policy
      return 0;
    }
    switch (type) {
    case CRITERIUM:
      return matcher.maxCriteriumPriority;
    case NUMBER_OF_CRITERIA:
      return matcher.criteriaUsed.size();
    default:
      return 0;
    }
  }

  /** Add criteriumContext to matcher: criteriaUsed, maxCriteriumPriority, drools expression.
   * <p>
   * Two examples for drools expressions:
   * <p>
   * itemTypeId == "96d4bdf1-5fc2-40ef-9ace-6d7e3e48ec4d"
   * <p>
   * loanTypeId in ("2e6f51b9-d00a-4f1d-9960-49b1977acfca", "220e2dad-e3c7-42f3-bb46-515ba29ba65f")
   */
  private void addCriterium(CriteriumContext criteriumContext, Matcher matcher) {
    String criteriumTypeLetter = criteriumContext.getStart().getText();

    switch(criteriumTypeLetter) {
    case "a":
    case "b":
    case "c":
    case "s":
      matcher.criteriaUsed.add("a");  // all location type letters count as one
      break;
    default:
      matcher.criteriaUsed.add(criteriumTypeLetter);
    }
    matcher.maxCriteriumPriority =
        Math.max(matcher.maxCriteriumPriority,
                 criteriumPriority.getOrDefault(criteriumTypeLetter, 0));

    matcher.drools.append("    ");
    String field = criteriumTypeClassname(criteriumTypeLetter);
    matcher.drools.append(field);

    if (criteriumContext.all() != null) {
      matcher.drools.append("() // all\n");
      return;
    }

    boolean not = false;
    TerminalNode terminal = criteriumContext.getChild(TerminalNode.class, 1);
    if (terminal != null && terminal.getText().equals("!")) {
      not = true;
    }

    if (criteriumContext.NAME().size() == 1) {
      matcher.drools.append(not ? "(id != " : "(id == " );
      appendQuotedString(matcher.drools, criteriumContext.NAME(0).getText());
      matcher.drools.append(")\n");
      return;
    }

    matcher.drools.append(not ? "(id not in (" : "(id in (");
    boolean first = true;
    for (int i=0; i<criteriumContext.NAME().size(); i++) {
      if (first) {
        first = false;
      } else {
        matcher.drools.append(", ");
      }
      appendQuotedString(matcher.drools, criteriumContext.NAME(i).getText());
    }
    matcher.drools.append("))\n");
  }

  /**
   * The class name of the criterium type.
   * @param letter one of t, a, b, c, s, m, g
   * @return the field name
   */
  private static String criteriumTypeClassname(String letter) {
    switch (letter) {
    case "t": return "LoanType";
    case "a": return "CampusLocation";
    case "b": return "BranchLocation";
    case "c": return "CollectionLocation";
    case "s": return "ShelvingLocation";
    case "m": return "ItemType";
    case "g": return "PatronGroup";
    default:  throw new IllegalArgumentException(
        "Expected criterium type t, a, b, c, s, m or g but found: " + letter);
    }
  }

  /**
   * Append name as quoted String to sb.
   * @param sb - where to add
   * @param name - the String to quote
   */
  private static void appendQuotedString(StringBuilder sb, String name) {
    sb.append('"');
    sb.append(StringEscapeUtils.escapeJava(name));
    sb.append('"');
  }
}
