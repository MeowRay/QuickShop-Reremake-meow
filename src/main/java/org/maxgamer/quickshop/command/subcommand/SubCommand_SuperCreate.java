/*
 * This file is a part of project QuickShop, the name is SubCommand_SuperCreate.java
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
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockIterator;
import org.jetbrains.annotations.NotNull;
import org.maxgamer.quickshop.QuickShop;
import org.maxgamer.quickshop.command.CommandHandler;
import org.maxgamer.quickshop.shop.Info;
import org.maxgamer.quickshop.shop.ShopAction;
import org.maxgamer.quickshop.util.MsgUtil;
import org.maxgamer.quickshop.util.Util;

import java.util.Collections;
import java.util.List;

@AllArgsConstructor
public class SubCommand_SuperCreate implements CommandHandler<Player> {

    private final QuickShop plugin;

    @Override
    public void onCommand(@NotNull Player sender, @NotNull String commandLabel, @NotNull String[] cmdArg) {
        ItemStack item = sender.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            MsgUtil.sendMessage(sender, "no-anythings-in-your-hand");
            return;
        }

        final BlockIterator bIt = new BlockIterator(sender, 10);

        while (bIt.hasNext()) {
            final Block b = bIt.next();

            if (!Util.canBeShop(b)) {
                continue;
            }

            if (cmdArg.length >= 1) {
                plugin.getShopManager().handleChat(sender, cmdArg[0], true);
                return;
            }
            // Send creation menu.
            final Info info = new Info(b.getLocation(), ShopAction.CREATE, sender.getInventory().getItemInMainHand(), b.getRelative(sender.getFacing().getOppositeFace()));

            plugin.getShopManager().getActions().put(sender.getUniqueId(), info);
            MsgUtil.sendMessage(sender, "how-much-to-trade-for", Util.getItemStackName(sender,item), Integer.toString(plugin.isAllowStack() && QuickShop.getPermissionManager().hasPermission(sender, "quickshop.create.stacks") ? item.getAmount() : 1));
            return;
        }
        MsgUtil.sendMessage(sender, "not-looking-at-shop");
    }

    @NotNull
    @Override
    public List<String> onTabComplete(
            @NotNull Player sender, @NotNull String commandLabel, @NotNull String[] cmdArg) {
        return cmdArg.length == 1 ? Collections.singletonList(MsgUtil.getMessage("tabcomplete.amount", sender)) : Collections.emptyList();
    }

}
