package org.niels.communityVault.ui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class VaultMenuHolder implements InventoryHolder {

    public enum Type {
        MAIN,
        MAIN_SELECT,
        CATEGORY,
        STACKS,
        SEARCH,
        CATEGORY_MANAGER,
        CATEGORY_EDITOR,
        ICON_PICKER,
        DELETE_CONFIRM,
        CATEGORY_ITEMS,
        CATEGORY_ADD_ITEMS
    }

    private final Type type;
    private final String parentName;
    private final int parentPage;
    private final String materialName;
    private final int page;

    public VaultMenuHolder(Type type) {
        this(type, null, 1, null, 1);
    }

    public VaultMenuHolder(Type type, String parentName, int parentPage, String materialName, int page) {
        this.type = type;
        this.parentName = parentName;
        this.parentPage = parentPage;
        this.materialName = materialName;
        this.page = page;
    }

    public Type getType() {
        return type;
    }

    public String getParentName() {
        return parentName;
    }

    public int getParentPage() {
        return parentPage;
    }

    public String getMaterialName() {
        return materialName;
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
