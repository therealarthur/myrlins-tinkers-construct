/**
 * Book fixes folded in from the private Continuum Core fork (1.12.1-arthur.1, commits 71e3a774 and a3d6c780), so
 * Myrlin's Tinker Remaster runs on stock Continuum Core 1.12.1 and still gets them. The client mixins in
 * {@code slimeknights.tconstruct.mixin.client.book} call into these helpers; {@link
 * slimeknights.tconstruct.library.client.book.corefix.CoreBookFixMixinPlugin} applies each mixin only when the
 * Core class still has the stock code it replaces, so the Core fork (or a later Core with the same fixes) is left alone.
 */
@ParametersAreNonnullByDefault
package slimeknights.tconstruct.library.client.book.corefix;

import javax.annotation.ParametersAreNonnullByDefault;
