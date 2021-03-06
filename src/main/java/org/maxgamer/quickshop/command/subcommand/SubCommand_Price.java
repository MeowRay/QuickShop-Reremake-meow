/*
 * This file is a part of project QuickShop, the name is SubCommand_Price.java
 *  Copyright (C) PotatoCraft Studio and contributors
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.maxgamer.quickshop.command.subcommand;

import lombok.AllArgsConstructor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;
import org.jetbrains.annotations.NotNull;
import org.maxgamer.quickshop.QuickShop;
import org.maxgamer.quickshop.command.CommandHandler;
import org.maxgamer.quickshop.economy.EconomyTransaction;
import org.maxgamer.quickshop.shop.ContainerShop;
import org.maxgamer.quickshop.shop.Shop;
import org.maxgamer.quickshop.util.MsgUtil;
import org.maxgamer.quickshop.util.PriceLimiter;
import org.maxgamer.quickshop.util.Util;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

@AllArgsConstructor
public class SubCommand_Price implements CommandHandler<Player> {

    private static final QuickShop plugin = QuickShop.getInstance();

    public static void call(@NotNull Player sender, @NotNull String commandLabel, PriceType type, @NotNull String[] cmdArg) {

        if (cmdArg.length < 1) {
            MsgUtil.sendMessage(sender, "no-price-given");
            return;
        }

        final double price;

        try {
            price = Double.parseDouble(cmdArg[0]);
        } catch (NumberFormatException ex) {
            // No number input
            Util.debugLog(ex.getMessage());
            MsgUtil.sendMessage(sender, "not-a-number", cmdArg[0]);
            return;
        }
        // No number input
        if (Double.isInfinite(price) || Double.isNaN(price)) {
            MsgUtil.sendMessage(sender, "not-a-number", cmdArg[0]);
            return;
        }

        final boolean format = plugin.getConfig().getBoolean("use-decimal-format");

        double fee = 0;

        if (plugin.isPriceChangeRequiresFee()) {
            fee = plugin.getConfig().getDouble("shop.fee-for-price-change");
        }

        final BlockIterator bIt = new BlockIterator(sender, 10);
        // Loop through every block they're looking at upto 10 blocks away
        if (!bIt.hasNext()) {
            MsgUtil.sendMessage(sender, "not-looking-at-shop");
            return;
        }

        PriceLimiter limiter = new PriceLimiter(
                plugin.getConfig().getDouble("shop.minimum-price"),
                plugin.getConfig().getInt("shop.maximum-price"),
                plugin.getConfig().getBoolean("shop.allow-free-shop"),
                plugin.getConfig().getBoolean("whole-number-prices-only"));

        while (bIt.hasNext()) {
            final Block b = bIt.next();
            final Shop shop = plugin.getShopManager().getShop(b.getLocation());

            if (shop == null
                    || (!shop.getModerator().isModerator(sender.getUniqueId())
                    && !QuickShop.getPermissionManager()
                    .hasPermission(sender, "quickshop.other.price"))) {
                continue;
            }

            if (type == PriceType.BUY_OR_SELL && shop.getPrice() == price) {
                // Stop here if there isn't a price change
                MsgUtil.sendMessage(sender, "no-price-change");
                return;
            } else if (shop.getSellPrice() == price) {
                MsgUtil.sendMessage(sender, "no-price-change");
                return;
            }

            PriceLimiter.CheckResult checkResult = limiter.check(shop.getItem(), price);
            if (checkResult.getStatus() == PriceLimiter.Status.REACHED_PRICE_MIN_LIMIT) {
                MsgUtil.sendMessage(sender, "price-too-cheap", (format) ? MsgUtil.decimalFormat(checkResult.getMin()) : Double.toString(checkResult.getMin()));
                return;
            }
            if (checkResult.getStatus() == PriceLimiter.Status.REACHED_PRICE_MAX_LIMIT) {
                MsgUtil.sendMessage(sender, "price-too-high", (format) ? MsgUtil.decimalFormat(checkResult.getMax()) : Double.toString(checkResult.getMax()));
                return;
            }
            if (checkResult.getStatus() == PriceLimiter.Status.PRICE_RESTRICTED) {
                MsgUtil.sendMessage(sender, "restricted-prices", Util.getItemStackName(shop.getItem()),
                        String.valueOf(checkResult.getMin()),
                        String.valueOf(checkResult.getMax()));
                return;
            }

            if (fee > 0) {
                EconomyTransaction transaction = EconomyTransaction.builder()
                        .allowLoan(plugin.getConfig().getBoolean("shop.allow-economy-loan", false))
                        .core(plugin.getEconomy())
                        .from(sender.getUniqueId())
                        .amount(fee)
                        .world(Objects.requireNonNull(shop.getLocation().getWorld()))
                        .currency(shop.getCurrency())
                        .build();
                if (!transaction.failSafeCommit()) {
                    EconomyTransaction.TransactionSteps steps = transaction.getSteps();
                    if (steps == EconomyTransaction.TransactionSteps.CHECK) {
                        MsgUtil.sendMessage(sender,

                                "you-cant-afford-to-change-price", plugin.getEconomy().format(fee, shop.getLocation().getWorld(), shop.getCurrency()));
                    } else {
                        MsgUtil.sendMessage(sender,

                                "fee-charged-for-price-change", plugin.getEconomy().format(fee, shop.getLocation().getWorld(), shop.getCurrency()));
                        plugin.getLogger().log(Level.WARNING, "QuickShop can't pay taxes to the configured tax account! Please set the tax account name in the config.yml to an existing player: " + transaction.getLastError());
                    }
                    return;
                }
            }
            // Update the shop
            if (type == PriceType.BUY_OR_SELL) {
                shop.setPrice(price);
            } else {
                shop.setSellPrice(price);
            }
            shop.update();

            if (type == PriceType.BUY_OR_SELL) {
                MsgUtil.sendMessage(sender,
                        "price-buy-or-sell-is-now", plugin.getEconomy().format(shop.getPrice(), Objects.requireNonNull(shop.getLocation().getWorld()), shop.getCurrency()));
            } else {
                MsgUtil.sendMessage(sender,
                        "price-sell-is-now", plugin.getEconomy().format(shop.getPrice(), Objects.requireNonNull(shop.getLocation().getWorld()), shop.getCurrency()));
            }

            // Chest shops can be double shops.
            if (!(shop instanceof ContainerShop)) {
                return;
            }

            final ContainerShop cs = (ContainerShop) shop;

            if (!cs.isDoubleShop()) {
                return;
            }

            final Shop nextTo = cs.getAttachedShop();

            if (nextTo == null) {
                // TODO: 24/11/2019 Send message about that issue.
                return;
            }

            if (cs.isSelling()) {
                if (cs.getPrice() < nextTo.getPrice()) {
                    MsgUtil.sendMessage(sender, "buying-more-than-selling");
                }
            }
            // Buying
            else if (cs.getPrice() > nextTo.getPrice()) {
                MsgUtil.sendMessage(sender, "buying-more-than-selling");
            }

            return;
        }
        MsgUtil.sendMessage(sender, "not-looking-at-shop");
    }

    @Override
    public void onCommand(@NotNull Player sender, @NotNull String commandLabel, @NotNull String[] cmdArg) {
        call(sender, commandLabel, PriceType.BUY_OR_SELL, cmdArg);
    }

    public enum PriceType {
        BUY_OR_SELL,
        SELL
    }

    @NotNull
    @Override
    public List<String> onTabComplete(
            @NotNull Player sender, @NotNull String commandLabel, @NotNull String[] cmdArg) {
        return cmdArg.length == 1 ? Collections.singletonList(MsgUtil.getMessage("tabcomplete.price", sender)) : Collections.emptyList();
    }

}
