package org.folio.circulation.support.http.client;

import static org.folio.circulation.support.http.client.Offset.offset;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

public class OffsetEqualityTests {
  @Test
  public void shouldNotBeEqualToAnotherObject() {
    final Offset offset = offset(5);

    assertThat(offset.equals(5), is(false));
  }

  @Test
  public void shouldNotBeEqualToDifferentOffset() {
    final Offset offset = offset(5);

    assertThat(offset.equals(offset(10)), is(false));
  }


  @Test
  public void shouldNotBeEqualToNull() {
    final Offset offset = offset(5);

    assertThat(offset.equals(null), is(false));
  }

  @Test
  public void shouldBeEqualToItself() {
    final Offset offset = offset(5);

    assertThat(offset.equals(offset), is(true));
  }

  @Test
  public void shouldBeEqualToOffsetWithSameDefinition() {
    final Offset firstOffset = offset(5);
    final Offset secondOffset = offset(5);

    assertThat(firstOffset.equals(secondOffset), is(true));
  }
}
