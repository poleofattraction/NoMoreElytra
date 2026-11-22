package ua.elytraban;

import io.papermc.paper.event.player.PlayerArmorChangeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class ElytraBan extends JavaPlugin implements Listener {

    private static final String BYPASS = "elytraban.bypass";
    private static final int CHEST_SLOT = 38; // слот нагрудника в PlayerInventory

    private static final Component MESSAGE =
            Component.text("Елітри на цьому сервері заборонені!", NamedTextColor.RED);

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    private static boolean isElytra(ItemStack item) {
        return item != null && item.getType() == Material.ELYTRA;
    }

    private void deny(Player player) {
        player.sendMessage(MESSAGE);
    }

    /** Знімає елітру з нагрудника і повертає в інвентар (або кидає під ноги). */
    private void stripElytra(Player player) {
        getServer().getScheduler().runTask(this, () -> {
            if (!player.isOnline()) return;
            ItemStack chest = player.getInventory().getChestplate();
            if (!isElytra(chest)) return;

            player.getInventory().setChestplate(null);
            player.getInventory().addItem(chest).values().forEach(left ->
                    player.getWorld().dropItemNaturally(player.getLocation(), left));
            deny(player);
        });
    }

    // 1. Кліки в інвентарі
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player) || player.hasPermission(BYPASS)) return;

        // Спроба покласти елітру прямо в слот нагрудника
        boolean chestSlot = e.getClickedInventory() instanceof PlayerInventory
                && e.getSlotType() == InventoryType.SlotType.ARMOR
                && e.getSlot() == CHEST_SLOT;

        if (chestSlot) {
            boolean bad = isElytra(e.getCursor())
                    || (e.getClick() == ClickType.NUMBER_KEY
                        && isElytra(player.getInventory().getItem(e.getHotbarButton())))
                    || (e.getClick() == ClickType.SWAP_OFFHAND
                        && isElytra(player.getInventory().getItemInOffHand()));
            if (bad) {
                e.setCancelled(true);
                deny(player);
                player.updateInventory();
                return;
            }
        }

        // Shift+клік по елітрі у власному інвентарі (авто-одягання)
        if (e.isShiftClick()
                && isElytra(e.getCurrentItem())
                && e.getClickedInventory() instanceof PlayerInventory
                && e.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            e.setCancelled(true);
            deny(player);
            player.updateInventory();
        }
    }

    // 2. ПКМ елітрою в руці
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isElytra(e.getItem()) || e.getPlayer().hasPermission(BYPASS)) return;

        e.setUseItemInHand(Event.Result.DENY);
        deny(e.getPlayer());
    }

    // 3. Роздатчик
    @EventHandler
    public void onDispense(BlockDispenseArmorEvent e) {
        if (isElytra(e.getItem()) && e.getTargetEntity() instanceof Player target
                && !target.hasPermission(BYPASS)) {
            e.setCancelled(true);
        }
    }

    // 4. Запасний варіант: будь-яка зміна броні (креатив, команди тощо)
    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent e) {
        Player player = e.getPlayer();
        if (player.hasPermission(BYPASS)) return;
        if (e.getSlotType() != PlayerArmorChangeEvent.SlotType.CHEST) return;
        if (!isElytra(e.getNewItem())) return;

        stripElytra(player);
    }

    // 5. Перевірка при вході
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (!player.hasPermission(BYPASS) && isElytra(player.getInventory().getChestplate())) {
            stripElytra(player);
        }
    }

    // 6. Блокування польоту
    @EventHandler
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.isGliding()
                && e.getEntity() instanceof Player player
                && !player.hasPermission(BYPASS)
                && isElytra(player.getInventory().getChestplate())) {
            e.setCancelled(true);
        }
    }
}
