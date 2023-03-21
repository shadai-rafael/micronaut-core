/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.expressions.parser;

import io.micronaut.core.annotation.Internal;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.ast.access.ContextElementAccess;
import io.micronaut.expressions.parser.ast.access.ContextMethodCall;
import io.micronaut.expressions.parser.ast.access.ElementMethodCall;
import io.micronaut.expressions.parser.ast.access.ListOrArrayAccess;
import io.micronaut.expressions.parser.ast.access.MapAccess;
import io.micronaut.expressions.parser.ast.access.PropertyAccess;
import io.micronaut.expressions.parser.ast.conditional.TernaryExpression;
import io.micronaut.expressions.parser.ast.literal.BoolLiteral;
import io.micronaut.expressions.parser.ast.literal.DoubleLiteral;
import io.micronaut.expressions.parser.ast.literal.FloatLiteral;
import io.micronaut.expressions.parser.ast.literal.IntLiteral;
import io.micronaut.expressions.parser.ast.literal.LongLiteral;
import io.micronaut.expressions.parser.ast.literal.NullLiteral;
import io.micronaut.expressions.parser.ast.literal.StringLiteral;
import io.micronaut.expressions.parser.ast.operator.binary.InstanceofOperator;
import io.micronaut.expressions.parser.ast.operator.binary.MatchesOperator;
import io.micronaut.expressions.parser.ast.operator.binary.PowOperator;
import io.micronaut.expressions.parser.ast.operator.binary.AndOperator;
import io.micronaut.expressions.parser.ast.operator.binary.OrOperator;
import io.micronaut.expressions.parser.ast.operator.binary.AddOperator;
import io.micronaut.expressions.parser.ast.operator.binary.DivOperator;
import io.micronaut.expressions.parser.ast.operator.binary.ModOperator;
import io.micronaut.expressions.parser.ast.operator.binary.MulOperator;
import io.micronaut.expressions.parser.ast.operator.binary.SubOperator;
import io.micronaut.expressions.parser.ast.operator.binary.EqOperator;
import io.micronaut.expressions.parser.ast.operator.binary.GtOperator;
import io.micronaut.expressions.parser.ast.operator.binary.GteOperator;
import io.micronaut.expressions.parser.ast.operator.binary.LtOperator;
import io.micronaut.expressions.parser.ast.operator.binary.LteOperator;
import io.micronaut.expressions.parser.ast.operator.binary.NeqOperator;
import io.micronaut.expressions.parser.ast.operator.unary.EmptyOperator;
import io.micronaut.expressions.parser.ast.operator.unary.NegOperator;
import io.micronaut.expressions.parser.ast.operator.unary.NotOperator;
import io.micronaut.expressions.parser.ast.operator.unary.PosOperator;
import io.micronaut.expressions.parser.ast.types.TypeIdentifier;
import io.micronaut.expressions.parser.exception.ExpressionParsingException;
import io.micronaut.expressions.parser.token.Token;
import io.micronaut.expressions.parser.token.TokenType;
import io.micronaut.expressions.parser.token.Tokenizer;

import java.util.ArrayList;
import java.util.List;

import static io.micronaut.expressions.parser.token.TokenType.BOOL;
import static io.micronaut.expressions.parser.token.TokenType.COLON;
import static io.micronaut.expressions.parser.token.TokenType.DECREMENT;
import static io.micronaut.expressions.parser.token.TokenType.DIV;
import static io.micronaut.expressions.parser.token.TokenType.DOT;
import static io.micronaut.expressions.parser.token.TokenType.DOUBLE;
import static io.micronaut.expressions.parser.token.TokenType.EMPTY;
import static io.micronaut.expressions.parser.token.TokenType.EQ;
import static io.micronaut.expressions.parser.token.TokenType.EXPRESSION_CONTEXT_REF;
import static io.micronaut.expressions.parser.token.TokenType.FLOAT;
import static io.micronaut.expressions.parser.token.TokenType.GT;
import static io.micronaut.expressions.parser.token.TokenType.GTE;
import static io.micronaut.expressions.parser.token.TokenType.R_SQUARE;
import static io.micronaut.expressions.parser.token.TokenType.TYPE_IDENTIFIER;
import static io.micronaut.expressions.parser.token.TokenType.INCREMENT;
import static io.micronaut.expressions.parser.token.TokenType.INSTANCEOF;
import static io.micronaut.expressions.parser.token.TokenType.INT;
import static io.micronaut.expressions.parser.token.TokenType.LONG;
import static io.micronaut.expressions.parser.token.TokenType.LT;
import static io.micronaut.expressions.parser.token.TokenType.LTE;
import static io.micronaut.expressions.parser.token.TokenType.L_PAREN;
import static io.micronaut.expressions.parser.token.TokenType.L_SQUARE;
import static io.micronaut.expressions.parser.token.TokenType.MATCHES;
import static io.micronaut.expressions.parser.token.TokenType.MINUS;
import static io.micronaut.expressions.parser.token.TokenType.MOD;
import static io.micronaut.expressions.parser.token.TokenType.MUL;
import static io.micronaut.expressions.parser.token.TokenType.NE;
import static io.micronaut.expressions.parser.token.TokenType.NOT;
import static io.micronaut.expressions.parser.token.TokenType.NULL;
import static io.micronaut.expressions.parser.token.TokenType.OR;
import static io.micronaut.expressions.parser.token.TokenType.PLUS;
import static io.micronaut.expressions.parser.token.TokenType.POW;
import static io.micronaut.expressions.parser.token.TokenType.QMARK;
import static io.micronaut.expressions.parser.token.TokenType.R_PAREN;
import static io.micronaut.expressions.parser.token.TokenType.SAFE_NAV;
import static io.micronaut.expressions.parser.token.TokenType.STRING;

/**
 * Parser for building AST for single evaluated expression.
 * A single expression is parsed as a whole,
 * it cannot contain multiple expressions.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class SingleEvaluatedEvaluatedExpressionParser implements EvaluatedExpressionParser {
    private final Tokenizer tokenizer;
    private Token lookahead;

    /**
     * Instantiates a parser for single passed expression.
     * Expression string must not contain expression template wrapper like #{...}
     *
     * @param expression expression to parse
     */
    public SingleEvaluatedEvaluatedExpressionParser(String expression) {
        this.tokenizer = new Tokenizer(expression);
        this.lookahead = tokenizer.getNextToken();
    }

    @Override
    public ExpressionNode parse() throws ExpressionParsingException {
        try {
            final ExpressionNode expressionNode = expression();
            if (lookahead != null) {
                throw new ExpressionParsingException("Unexpected token: " + lookahead.value());
            }
            return expressionNode;
        } catch (NullPointerException ex) {
            throw new ExpressionParsingException("Unexpected end of input");
        }
    }

    // Expression
    //  : TernaryExpression
    //  ;
    private ExpressionNode expression() {
        return ternaryExpression();
    }

    // TernaryExpression
    //  : OrExpression
    //  | OrExpression '?' Expression ':' Expression
    //  ;
    private ExpressionNode ternaryExpression() {
        ExpressionNode orExpression = orExpression();
        if (lookahead != null && lookahead.type() == QMARK) {
            eat(QMARK);
            ExpressionNode trueExpr = expression();
            eat(COLON);
            ExpressionNode falseExpr = expression();
            return new TernaryExpression(orExpression, trueExpr, falseExpr);
        }
        return orExpression;
    }

    // OrExpression
    //   : AndExpression
    //   | OrExpression '||' AndExpression -> AndExpression '||' AndExpression '||' AndExpression
    //   ;
    private ExpressionNode orExpression() {
        ExpressionNode leftNode = andExpression();
        while (lookahead != null && lookahead.type() == OR) {
            eat(OR);
            leftNode = new OrOperator(leftNode, andExpression());
        }
        return leftNode;
    }

    // AndExpression
    //   : EqualityExpression
    //   | AndExpression '&&' EqualityExpression
    //   ;
    private ExpressionNode andExpression() {
        ExpressionNode leftNode = equalityExpression();
        while (lookahead != null && lookahead.type() == TokenType.AND) {
            eat(TokenType.AND);
            leftNode = new AndOperator(leftNode, equalityExpression());
        }
        return leftNode;
    }

    // EqualityExpression
    //  : RelationalExpression
    //  | EqualityExpression '==' RelationalExpression
    //  | EqualityExpression '!=' RelationalExpression
    //  ;
    private ExpressionNode equalityExpression() {
        ExpressionNode leftNode = relationalExpression();
        while (lookahead != null && lookahead.type().isOneOf(EQ, NE)) {
            TokenType tokenType = lookahead.type();
            eat(tokenType);
            if (tokenType == EQ) {
                leftNode = new EqOperator(leftNode, relationalExpression());
            } else if (tokenType == NE) {
                leftNode = new NeqOperator(leftNode, relationalExpression());
            }
        }
        return leftNode;
    }

    // RelationalExpression
    //  : AdditiveExpression
    //  | RelationalExpression RelOperator AdditiveExpression
    //  | RelationalExpression 'instanceof' TypeIdentifier
    //  | RelationalExpression 'matches' StringLiteral
    //  ;
    private ExpressionNode relationalExpression() {
        ExpressionNode leftNode = additiveExpression();
        while (lookahead != null && (lookahead.type()
                                         .isOneOf(GT, GTE, LT, LTE, INSTANCEOF, MATCHES))) {
            TokenType tokenType = lookahead.type();
            eat(lookahead.type());
            leftNode = switch (tokenType) {
                case GT -> new GtOperator(leftNode, additiveExpression());
                case LT -> new LtOperator(leftNode, additiveExpression());
                case GTE -> new GteOperator(leftNode, additiveExpression());
                case LTE -> new LteOperator(leftNode, additiveExpression());
                case INSTANCEOF -> new InstanceofOperator(leftNode, typeIdentifier());
                case MATCHES -> new MatchesOperator(leftNode, stringLiteral());
                default -> leftNode;
            };
        }
        return leftNode;
    }

    // AdditiveExpression
    //  : PowExpression
    //  | AdditiveExpression '+' PowExpression
    //  | AdditiveExpression '-' PowExpression
    //  ;
    private ExpressionNode additiveExpression() {
        ExpressionNode leftNode = multiplicativeExpression();
        while (lookahead != null && lookahead.type().isOneOf(PLUS, MINUS)) {
            TokenType tokenType = lookahead.type();
            eat(tokenType);
            if (tokenType == PLUS) {
                leftNode = new AddOperator(leftNode, multiplicativeExpression());
            } else if (tokenType == MINUS) {
                leftNode = new SubOperator(leftNode, multiplicativeExpression());
            }
        }
        return leftNode;
    }

    // MultiplicativeExpression
    //  : PowExpression
    //  | MultiplicativeExpression '*' PowExpression
    //  | MultiplicativeExpression '/' PowExpression
    //  | MultiplicativeExpression '%' PowExpression
    //  ;
    private ExpressionNode multiplicativeExpression() {
        ExpressionNode leftNode = powExpression();
        while (lookahead != null && lookahead.type().isOneOf(MUL, DIV, MOD)) {
            TokenType tokenType = lookahead.type();
            eat(tokenType);
            if (tokenType == MUL) {
                leftNode = new MulOperator(leftNode, powExpression());
            } else if (tokenType == DIV) {
                leftNode = new DivOperator(leftNode, powExpression());
            } else if (tokenType == MOD) {
                leftNode = new ModOperator(leftNode, powExpression());
            }
        }
        return leftNode;
    }

    // PowExpression
    //  : UnaryExpression
    //  | PowExpression '^' UnaryExpression
    //  ;
    private ExpressionNode powExpression() {
        ExpressionNode leftNode = unaryExpression();
        while (lookahead != null && lookahead.type() == POW) {
            eat(POW);
            leftNode = new PowOperator(leftNode, unaryExpression());
        }
        return leftNode;
    }

    // UnaryExpression
    //  : '+'  UnaryExpression
    //  | '-'  UnaryExpression
    //  | '!'  UnaryExpression
    //  | '++' UnaryExpression
    //  | '--' UnaryExpression
    //  | PostfixExpression
    //  ;
    private ExpressionNode unaryExpression() {
        TokenType tokenType = lookahead.type();
        if (tokenType == PLUS) {
            eat(PLUS);
            return new PosOperator(unaryExpression());
        } else if (tokenType == MINUS) {
            eat(MINUS);
            return new NegOperator(unaryExpression());
        } else if (tokenType == NOT) {
            eat(NOT);
            return new NotOperator(unaryExpression());
        } else if (tokenType == EMPTY) {
            eat(EMPTY);
            return new EmptyOperator(unaryExpression());
        } else if (tokenType == INCREMENT) {
            throw new ExpressionParsingException("Prefix increment operation is not supported");
        } else if (tokenType == DECREMENT) {
            throw new ExpressionParsingException("Prefix decrement operation is not supported");
        } else {
            return postfixExpression();
        }
    }

    // PostfixExpression
    //  : PrimaryExpression
    //  | PostfixExpression '.'  MethodOrPropertyAccess
    //  | PostfixExpression '?.'  MethodOrPropertyAccess with safe navigation
    //  | PostfixExpression '++'
    //  | PostfixExpression '--'
    //  ;
    private ExpressionNode postfixExpression() {
        ExpressionNode leftNode = primaryExpression();
        while (lookahead != null && (lookahead.type()
                                         .isOneOf(DOT, SAFE_NAV, L_SQUARE, INCREMENT, DECREMENT))) {
            TokenType tokenType = lookahead.type();
            if (tokenType == INCREMENT) {
                throw new ExpressionParsingException("Postfix increment operation is not " +
                                                         "supported");
            } else if (tokenType == DECREMENT) {
                throw new ExpressionParsingException("Postfix decrement operation is not " +
                                                         "supported");
            } else if (tokenType == DOT) {
                eat(DOT);
                leftNode = methodOrPropertyAccess(leftNode, false);
            } else if (tokenType == SAFE_NAV) {
                eat(SAFE_NAV);
                leftNode = methodOrPropertyAccess(leftNode, true);
            } else if (tokenType == L_SQUARE) {
                eat(L_SQUARE);
                leftNode = mapOrListAccess(leftNode);
            } else {
                throw new ExpressionParsingException("Unexpected token: " + lookahead.value());
            }
        }
        return leftNode;
    }

    // PrimaryExpression
    //  : ContextAccess
    //  | TypeIdentifier
    //  | ParenthesizedExpression
    //  | Literal
    //  ;
    private ExpressionNode primaryExpression() {
        return switch (lookahead.type()) {
            case EXPRESSION_CONTEXT_REF -> contextAccess();
            case IDENTIFIER -> identifier();
            case TYPE_IDENTIFIER -> typeIdentifier();
            case L_PAREN -> parenthesizedExpression();
            case STRING, INT, LONG, DOUBLE, FLOAT, BOOL, NULL -> literal();
            default -> throw new ExpressionParsingException("Unexpected token: " + lookahead.value());
        };
    }

    // ContextAccess
    //  : '#' Identifier
    //  | '#' Identifier MethodArguments
    //  ;
    private ExpressionNode contextAccess() {
        eat(EXPRESSION_CONTEXT_REF);
        return primaryExpression();
    }

    private ExpressionNode identifier() {
        String identifier = eat(TokenType.IDENTIFIER).value();
        if (lookahead != null && lookahead.type() == L_PAREN) {
            List<ExpressionNode> methodArguments = methodArguments();
            return new ContextMethodCall(identifier, methodArguments);
        }
        return new ContextElementAccess(identifier);
    }

    // MethodOrFieldAccess
    //  : SimpleIdentifier
    //  | SimpleIdentifier MethodArguments
    //  ;
    private ExpressionNode methodOrPropertyAccess(ExpressionNode callee, boolean nullSafe) {
        String identifier = eat(TokenType.IDENTIFIER).value();
        if (lookahead != null && lookahead.type() == L_PAREN) {
            List<ExpressionNode> methodArguments = methodArguments();
            return new ElementMethodCall(callee, identifier, methodArguments, nullSafe);
        }
        return new PropertyAccess(callee, identifier, nullSafe);
    }

    // MapOrListAccess
    //  : SimpleIdentifier
    //  | SimpleIdentifier []
    //  ;
    private ExpressionNode mapOrListAccess(ExpressionNode callee) {
        if (lookahead != null) {
            if (lookahead.type() == INT) {
                ListOrArrayAccess listOrArrayAccess = new ListOrArrayAccess(
                    callee,
                    intLiteral().getValue()
                );
                eat(R_SQUARE);
                return listOrArrayAccess;
            } else if (lookahead.type() == STRING) {
                MapAccess mapAccess = new MapAccess(callee, stringLiteral().getValue());
                eat(R_SQUARE);
                return mapAccess;
            } else {
                throw new ExpressionParsingException("Unexpected token: " + lookahead.value());
            }
        } else {
            throw new ExpressionParsingException("Unclosed subscript operator");
        }
    }

    // MethodArguments:
    //  '(' MethodArgumentsList ')'
    //  ;
    private List<ExpressionNode> methodArguments() {
        eat(L_PAREN);
        List<ExpressionNode> arguments = new ArrayList<>();
        if (lookahead.type() != R_PAREN) {
            arguments = methodArgumentsList();
        }
        eat(R_PAREN);
        return arguments;
    }

    // MethodArgumentsList
    //  : Expression
    //  | MethodArgumentsList ',' Expression
    //  ;
    private List<ExpressionNode> methodArgumentsList() {
        List<ExpressionNode> arguments = new ArrayList<>();
        if (lookahead.type() != R_PAREN) {
            ExpressionNode firstArgument = expression();
            arguments.add(firstArgument);

            while (lookahead.type() != R_PAREN) {
                eat(TokenType.COMMA);
                arguments.add(expression());
            }
        }
        return arguments;
    }

    // TypeReference
    //   : 'T(' ChainedIdentifier')'
    //   ;
    private TypeIdentifier  typeIdentifier() {
        eat(TYPE_IDENTIFIER);
        List<String> parts = new ArrayList<>();
        parts.add(eat(TokenType.IDENTIFIER).value());
        while (lookahead != null && lookahead.type() == DOT) {
            eat(DOT);
            parts.add(eat(TokenType.IDENTIFIER).value());
        }
        eat(R_PAREN);
        return new TypeIdentifier(String.join(".", parts));
    }

    // ParenthesizedExpression
    //  : '(' Expression ')'
    //  ;
    private ExpressionNode parenthesizedExpression() {
        eat(L_PAREN);
        ExpressionNode parenthesizedExpression = expression();
        eat(R_PAREN);
        return parenthesizedExpression;
    }

    // Literal
    //  : StringLiteral
    //  | IntLiteral
    //  | LongLiteral
    //  | DecimalLiteral
    //  | FloatLiteral
    //  | BoolLiteral
    //  ;
    private ExpressionNode literal() {
        return switch (lookahead.type()) {
            case DOUBLE -> doubleLiteral();
            case FLOAT -> floatLiteral();
            case INT -> intLiteral();
            case STRING -> stringLiteral();
            case LONG -> longLiteral();
            case BOOL -> boolLiteral();
            case NULL -> nullLiteral();
            default -> throw new ExpressionParsingException("Unknown literal type: " + lookahead.type());
        };
    }

    private StringLiteral stringLiteral() {
        Token token = eat(STRING);
        String value = token.value();
        // removing surrounding quotes
        return new StringLiteral(token.value().substring(1, value.length() - 1));
    }

    private DoubleLiteral doubleLiteral() {
        Token token = eat(DOUBLE);
        return new DoubleLiteral(Double.parseDouble(token.value()));
    }

    private FloatLiteral floatLiteral() {
        Token token = eat(FLOAT);
        return new FloatLiteral(Float.parseFloat(token.value()));
    }

    private IntLiteral intLiteral() {
        Token token = eat(INT);
        return new IntLiteral(Integer.decode(token.value()));
    }

    private LongLiteral longLiteral() {
        Token token = eat(LONG);
        return new LongLiteral(Long.decode(token.value().replaceAll("([lL])", "")));
    }

    private BoolLiteral boolLiteral() {
        Token token = eat(BOOL);
        return new BoolLiteral(Boolean.parseBoolean(token.value()));
    }

    private NullLiteral nullLiteral() {
        eat(NULL);
        return new NullLiteral();
    }

    private Token eat(TokenType tokenType) {
        if (lookahead == null) {
            throw new ExpressionParsingException("Unexpected end of input. Expected: '" + tokenType + "'");
        }

        Token token = lookahead;
        if (token.type() != tokenType) {
            throw new ExpressionParsingException("Unexpected token: " + token.value() + ". Expected: '" + tokenType + "'");
        }

        lookahead = tokenizer.getNextToken();
        return token;
    }
}
