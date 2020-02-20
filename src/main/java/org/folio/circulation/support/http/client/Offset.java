package org.folio.circulation.support.http.client;

import java.util.Objects;

public class Offset implements QueryParameter {
  private final Integer value;

  public static Offset offset(int value) {
    return new Offset(value);
  }

  public static Offset noOffset() {
    return new Offset(0);
  }

  private Offset(Integer value) {
    this.value = value;
  }

  @Override
  public void consume(QueryStringParameterConsumer consumer) {
    consumer.consume("offset", value.toString());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (!(o instanceof Offset)) return false;

    Offset offset = (Offset) o;

    return Objects.equals(value, offset.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
