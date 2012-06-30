package org.wargamer2010.capturetheportal;

import org.wargamer2010.capturetheportal.Utils.Util;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.Location;
import org.bukkit.ChatColor;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.Bukkit;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.File;
import java.util.List;
import java.util.ArrayList;

import org.wargamer2010.capturetheportal.hooks.*;

public class CaptureThePortal extends JavaPlugin {
    private int capturedelay = 60;                                                              // How long it takes in deciseconds (1/10th of a second)
    private int cooldown_time = 1200;                                                           // How long the cooldown is before a Portal can be recaptured in deciseconds    
    private static Boolean fullgroupnames = false;                                                     // Whether or not to use the full group name where possible
    private int cooldown_message_timeleft_increments = 0;                                       // How much time passes before the cooldown_message is printed, thus it's printer every x increments (0 is disabled)
    private int cooldown_message_timeleft = 20;                                                 // The amount of cooldowntime that is left before the cooldown_message is printed    
    private int squareSize = 5;                                                                 // The size of each side of the square + 1
    private boolean persistcapture = true;                                                      // Whether or not to store and load captured points
    private boolean dieorbounce = false;                                                        // Whether the player that attempts to use an uncaptured portal dies (true) or bounces off (false)
    private boolean enablewormholes = false;                                                    // Whether or not Wormholes should be supported together with regular nether portals
    private boolean enablestargates = false;                                                    // Whether or not Stargates should be supported together with regular nether portals
    private boolean enablefactions = false;                                                     // Enables factions and disables default way of creating teams (via Permissions)
    private boolean enable_ender = false;                                                       // Support for Ender portals, is disabled by default    
    private boolean enabletowny = false;                                                        // Enables towny support
    private boolean enablesimpleclans = false;                                                  // Enables simpleclans support
    private boolean enablepermissions = false;                                                  // Enables permissions support    
    
    private static HashMap<Location, PortalCooldown> Timers;                                    // Stores all the timing classes (CapturePortal) for the various locations
    public static Map<String, Integer> Colors;                                                  // Stores all the Permissions with their respective color codes        
    public static CapturesStorage Storage;
    
    private static CaptureThePortalListener PlayerListener;
    
    private static final Logger logger = Logger.getLogger("Minecraft");
    private static final String pluginName = "CaptureThePortal";
    private Hook groupPlugin;

    @Override
    public void onDisable()
    {
        if(persistcapture)
            Storage.saveCaptures();
        else {
            if(Storage.getAllCaptures() != null)
                for (Location key : Storage.getAllCaptures().keySet()) {
                    Block center = key.getWorld().getBlockAt(key);
                    Block woolCenter = key.getWorld().getBlockAt(center.getX(), (center.getY()-1), center.getZ());
                    this.colorSquare(woolCenter, key.getWorld(), 0);                
                }
            Storage.clear();
        }
        log("Disabled", Level.INFO);
        
    }

    @Override
    public void onEnable()
    {
        PluginManager pm = getServer().getPluginManager();
        initConfig();        
        if(pm.getPlugin("Factions") == null && enablefactions) {
            enablefactions = false;
            log("Faction support enabled in config but Factions plugin is not enabled!", Level.WARNING);
        } 
        if(pm.getPlugin("Towny") == null && enabletowny) {
            enabletowny = false;
            log("Towny support enabled in config but Towny plugin is not enabled!", Level.WARNING);
        }
        if(pm.getPlugin("SimpleClans") == null && enablesimpleclans) {
            enablesimpleclans = false;
            log("SimpleClans support enabled in config but SimpleClans plugin is not enabled!", Level.WARNING);
        }
        if(pm.getPlugin("Permissions") == null && enablepermissions) {
            enablepermissions = false;
            log("Permissions support enabled in config but Permissions plugin is not enabled!", Level.WARNING);
        } else if(pm.getPlugin("Permissions") != null)
            enablepermissions = true;
        
        if(enablepermissions || enablefactions || enabletowny || enablesimpleclans) {
            int groupplugins = 0;            
            if(enablefactions) {
                groupPlugin = new FactionsHook();                
                groupplugins++;
                enablepermissions = false;
            }
            if(enabletowny) {
                groupPlugin = new TownyAdvancedHook(pm.getPlugin("Towny"));                
                groupplugins++;
                enablepermissions = false;
            }
            if(enablesimpleclans) {
                groupPlugin = new SimpleclansHook(pm.getPlugin("SimpleClans"));                
                groupplugins++;
                enablepermissions = false;
            }
            if(enablepermissions) {                
                groupPlugin = new PermissionsHook(pm.getPlugin("Permissions"));                
                groupplugins++;
            }
            if(groupplugins > 1) {
                log("Please only enable one Group plugin, so Factions OR Towny OR Simpleclans!", Level.SEVERE);                
                return;
            }
            
            log("Using " + groupPlugin.getName() +  " as Group plugin", Level.INFO);
            
            PlayerListener = new CaptureThePortalListener(this);                        
            Storage = new CapturesStorage(new File(this.getDataFolder(),"capturedpoints.yml"), persistcapture);            
            Colors = new HashMap();
            Timers = new HashMap();
            initColors();
            if(persistcapture)
                spinCooldowns();
            pm.registerEvents(PlayerListener, this);
            log("Enabled", Level.INFO);
        } else {
            // Permission system could not be hooked, running plugin passively..
            log("Permission system not detected and no other Group plugin found or not enabled in config. Plugin can't run", Level.SEVERE);            
            return;
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String args[]) {
        String commandName = cmd.getName().toLowerCase();        
        if(!commandName.equalsIgnoreCase("capturetheportal"))
            return true;
        if(args.length != 1)
            return false;
        if((sender instanceof Player) && !((Player)sender).isOp()) {
            ((Player)sender).sendMessage(ChatColor.RED + "You are not allowed to use that command. OP only.");
            return true;
        }
        if(args[0].equals("reload")) {
            Bukkit.getServer().getPluginManager().disablePlugin(this);
            Bukkit.getServer().getPluginManager().enablePlugin(this);
        } else
            return false;
        log("Reloaded", Level.INFO);
        if((sender instanceof Player))
            ((Player)sender).sendMessage(ChatColor.GREEN + "CaptureThePortal has been reloaded");
        return true;
    }
    
    public void spinCooldowns() {
        PortalCooldown pc;
        for (Map.Entry<Location, CapturesStorage.CaptureInformation> entry : Storage.getAllCaptures().entrySet()) {                     
            if(entry.getValue().cooldownleft > 0) {
                int decremented = 0;
                if(cooldown_message_timeleft_increments > 0 && entry.getValue().cooldownleft > cooldown_message_timeleft_increments)
                    decremented = (cooldown_message_timeleft_increments - (entry.getValue().cooldownleft % cooldown_message_timeleft_increments));
                pc = new PortalCooldown(this, entry.getKey().getBlock(), (entry.getValue().cooldownleft*10), entry.getValue().group, "cooldown", decremented, null);
                addTimer(entry.getKey().getBlock().getLocation(), pc);
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, pc, 10);
            }
        }
    }
    
    public String getGroupType() {        
        return groupPlugin.getGroupType();        
    }
    
    public void log(String message, Level lvl) {
        PluginDescriptionFile pdfFile = this.getDescription();
        logger.log(lvl, ("["+pdfFile.getName()+" v"+pdfFile.getVersion()+"] " + message));
    }
    
    public static void Log(String message, Level lvl) {        
        logger.log(lvl, ("["+pluginName+"] " + message));
    }
    
    public String getMessage(String message) {                
        return (ChatColor.DARK_AQUA + "[CTP]: " + ChatColor.WHITE + this.getConfig().getString(("CaptureThePortal.messages." + message), ""));
    }
    
    private void initConfig() {
        this.reloadConfig();
        FileConfiguration config = this.getConfig();
        File configFile = new File("plugins/CaptureThePortal", "config.yml");
        if(!configFile.exists())
            this.saveDefaultConfig();
        config.options().copyDefaults(true);
        capturedelay = (config.getInt("CaptureThePortal.delay", 0)*10);         // Convert to deciseconds
        cooldown_time = (config.getInt("CaptureThePortal.cooldown", 0)*10);        
        cooldown_message_timeleft = (config.getInt("CaptureThePortal.cooldown_message_timeleft", 0)*10);
        cooldown_message_timeleft_increments = config.getInt("CaptureThePortal.cooldown_message_timeleft_increments", 0);        
        fullgroupnames = (config.getBoolean("CaptureThePortal.fullgroupnames", fullgroupnames));
        dieorbounce = (config.getBoolean("CaptureThePortal.dieorbounce", dieorbounce));
        persistcapture = (config.getBoolean("CaptureThePortal.persistcapture", persistcapture));        
        enablewormholes = (config.getBoolean("CaptureThePortal.enablewormholesupport", enablewormholes));
        enablestargates = (config.getBoolean("CaptureThePortal.enablestargatesupport", enablestargates));
        enablefactions = (config.getBoolean("CaptureThePortal.enablefactionsupport", enablefactions));
        enabletowny = (config.getBoolean("CaptureThePortal.enabletownysupport", enabletowny));
        enablesimpleclans = (config.getBoolean("CaptureThePortal.enablesimpleclanssupport", enablesimpleclans));
        enable_ender = (config.getBoolean("CaptureThePortal.enableEndersupport", enable_ender));
        
        log("Configuration loaded succesfully", Level.INFO);
        this.saveConfig();
    }

    private void initColors() {
        /*
         * 1 = Orange
         * 2 = (light)Purple
         * 3 = (light)Blue
         * 4 = Yellow
         * 5 = (light)Green
         * 6 = (light)Pink
         * 7 = Grey
         * 8 = (light)Grey
         * 9 = (light)Blue
         * 10 = Purple
         * 11 = Blue
         * 12 = Brown
         * 13 = Green
         * 14 = Red
         * 15 = Black
         * 16 = White
         */
        Colors.put("gold", 1);
        Colors.put("light_purple", 2);
        Colors.put("blue", 3);
        Colors.put("yellow", 4);
        Colors.put("green", 5);
        Colors.put("dark_gray", 7);
        Colors.put("gray", 8);
        Colors.put("dark_purple", 10);
        Colors.put("dark_blue", 11);        
        Colors.put("dark_green", 13);
        Colors.put("red", 14);
        Colors.put("black", 15);
        
        Colors.put("default", 14);
    }

    private boolean checkSquare(Block block, World world) {
        boolean isWoolSquare = true;
        
        for(int x = -((squareSize-1)/2); x <= ((squareSize-1)/2); x++) {
            for(int z = -((squareSize-1)/2); z <= ((squareSize-1)/2); z++) {
                if(world.getBlockAt(block.getX() + x, block.getY(), block.getZ() + z).getType() != Material.WOOL)
                    isWoolSquare = false;
            }
        }
        
        return isWoolSquare;
    }
    
    
    private boolean checkWormHoleDailer(Block block, World world) {
        BlockFace dailerOrientation = BlockFace.SELF;        
        
        for(int x = -1; x <= 1; x++) {
            for(int z = -1; z <= 1; z++) {
                if(x == 0 && z == 0) continue;
                Block requireWool = world.getBlockAt(block.getX()+x, block.getY(), block.getZ()+z);
                if(requireWool.getType().equals(Material.OBSIDIAN)
                        && requireWool.getRelative(BlockFace.UP).getType().equals(Material.OBSIDIAN)) {
                    
                    dailerOrientation = Util.getFaceWithMaterial(Material.WOOL, requireWool);
                    if(dailerOrientation == BlockFace.SELF) return false;

                    BlockFace forward = Util.getOrientation(dailerOrientation, "cont");
                    BlockFace backward = Util.getOrientation(forward, "opp");
                    BlockFace otherside = Util.getOrientation(dailerOrientation, "opp");
                    
                    Block requireLever = world.getBlockAt(block.getX()+x, block.getY()+1, block.getZ()+z);
                    Block requireObsidian = world.getBlockAt(block.getX()+x, block.getY(), block.getZ()+z);
                    
                    if((requireLever.getRelative(forward).getType() == Material.LEVER
                            || requireLever.getRelative(backward).getType() == Material.LEVER)
                        && requireObsidian.getRelative(otherside).getType() == Material.OBSIDIAN
                        && requireObsidian.getRelative(otherside).getRelative(BlockFace.UP).getType() == Material.OBSIDIAN
                        && (requireObsidian.getRelative(otherside).getRelative(BlockFace.UP).getRelative(forward).getType() == Material.WALL_SIGN
                            || requireObsidian.getRelative(otherside).getRelative(BlockFace.UP).getRelative(backward).getType() == Material.WALL_SIGN))
                        return true;
                    else
                        continue;
                }                
            }
        }
        return false;
    }
    
    private boolean checkEndPortal(Block block, World world) {
        int radius = 3;
        for(int x = -radius; x <= radius; x++) {
            for(int z = -radius; z <= radius; z++) {
                for(int y = -radius; y <= radius; y++) {
                    if(world.getBlockAt(block.getX()+x, block.getY(), block.getZ()+z).getType() == Material.ENDER_PORTAL)
                        return true;
                }
            }
        }
        return false;
    }
    
    private boolean checkStargatePortal(Block woolblock, World world) {
        Block button = world.getBlockAt(woolblock.getX(), woolblock.getY()+1, woolblock.getZ());
        List<BlockFace> faces = new ArrayList();
        faces.add(BlockFace.NORTH);        
        faces.add(BlockFace.EAST);
        faces.add(BlockFace.SOUTH);
        faces.add(BlockFace.WEST);        
        for(BlockFace face : faces) {            
            if(button.getRelative(face).getType() == Material.OBSIDIAN) {                
                for(BlockFace sface : faces) {                    
                    if(button.getRelative(face).getRelative(sface).getType() == Material.STONE_BUTTON)
                        return true;
                }
            }
        }        
        return false;
    }

    public String getTeamOfPlayer(Player player) {
        return groupPlugin.getGroupByPlayer(player);
    }
    
    public void colorSquare(Block center, World world, int color) {        
        if(checkSquare(center, world)) {
            for(int x = -((squareSize-1)/2); x <= ((squareSize-1)/2); x++) {
                for(int z = -((squareSize-1)/2); z <= ((squareSize-1)/2); z++) {
                    if(world.getBlockAt(center.getX() + x, center.getY(), center.getZ() + z).getType() == Material.WOOL)
                        world.getBlockAt(center.getX() + x, center.getY(), center.getZ() + z).setData((byte)(color));
                }
            }
        } else
            center.setData((byte)(color));
    }
    
    private String validCapture(Block block, Player player) {
        String captureType = "";
        Block woolCenter = player.getWorld().getBlockAt(block.getX(), (block.getY()-1), block.getZ());
        if(this.enable_ender && checkEndPortal(woolCenter, player.getWorld()))
            captureType = "End";
        else if(this.enablewormholes && checkWormHoleDailer(woolCenter, player.getWorld()))
            captureType = "Wormhole";
        else if(this.enablestargates && checkStargatePortal(woolCenter, player.getWorld()))
            captureType = "Startgate";
        else if(checkSquare(woolCenter, player.getWorld()))
            captureType = "Nether";                
        if(!getTeamOfPlayer(player).equals(""))
            if(!Storage.getCapture(block.getLocation()).equals(getTeamOfPlayer(player)))
                return captureType;
        return "";
    }
    
    private void broadcastCapture(Player player, String captureType) {
        if(!getMessage("capture_message").equals("")) {
            String team = "";
            ChatColor chat;
            team = groupPlugin.getGroupByPlayer(player);
            if(enablepermissions)            
                chat = groupPlugin.getGroupColor(player);
            else
                chat = ChatColor.WHITE;
            Util.broadcastMessage(ChatColor.GREEN+getMessage("capture_message").replace("[team]", chat+team+ChatColor.GREEN).replace("[type]", captureType).replace("[group]", getGroupType()));
        }
    }
    
    public boolean capturePortal(Block block, Player player) {
        String captureType;
        if(!(captureType = validCapture(block, player)).equals("")) {
            if(Timers.containsKey(block.getLocation()) && Timers.get(block.getLocation()).getCooldown() > 0 && Timers.get(block.getLocation()).getType().equals("cooldown")) {
                Util.sendMessagePlayer(getMessage("player_portal_is_cooldown").replace("[cooldown]", Util.parseTime(Timers.get(block.getLocation()).getCooldown()/10)+ "."), player);
                return false;
            } else if (Timers.containsKey(block.getLocation()) && Timers.get(block.getLocation()).getCooldown() > 0 && Timers.get(block.getLocation()).getType().equals("delay")) {
                if(Timers.get(block.getLocation()).getCapturer() != player)
                    Util.sendMessagePlayer(getMessage("player_someone_else_capturing"), player);
                return false;
            } else if(Timers.containsKey(block.getLocation()) && Timers.get(block.getLocation()).getCooldown() == 0) {
                Timers.remove(block.getLocation());
            }
            broadcastCapture(player, captureType);
            Util.sendMessagePlayer(getMessage("player_capturing_portal_holdit").replace("[capturetime]", Util.parseTime(capturedelay/10)), player);
            getServer().getScheduler().scheduleSyncDelayedTask(this, new CapturePortal(this, player, block, cooldown_time, capturedelay, player.getLocation()), 1);
            return true;
        } else
            return false;
    }
    
    public int getCapturedelay() {
        return capturedelay;
    }

    public void addCaptureLocation(Block block, String group, Integer cooldownleft) {        
        Storage.setCapture(block.getLocation(), group, cooldownleft);
    }

    public void addTimer(Location loc, PortalCooldown pc) {
        if(Timers.containsKey(loc))
            Timers.remove(loc);
        Timers.put(loc, pc);
    }

    public int isAllowedToPortal(Block block, Player player, Material portalMaterial) {
        Block checkBlock;
        int xradius, yradius, zradius;        
        if(portalMaterial == Material.STATIONARY_WATER) {
            xradius = 5;
            zradius = 5;
            yradius = 6;
        } else {
            xradius = 3;
            zradius = 3;
            yradius = 5;
        }
        for(int x = -xradius; x <= xradius; x++) {
            for(int z = -zradius; z <= zradius; z++) {
                for(int y = -yradius; y <= yradius; y++) {
                    checkBlock = player.getWorld().getBlockAt((int)(player.getLocation().getX() + x), (int)(player.getLocation().getY() + y), (int)(player.getLocation().getZ() + z));                    
                    if(checkBlock.getType() == Material.STONE_PLATE) {
                        
                        if(!Storage.getCapture(checkBlock.getLocation()).equals(getTeamOfPlayer(player))                            
                            && !groupPlugin.isAllied(player, Storage.getCapture(checkBlock.getLocation())))
                            return 0;
                        
                        if(Storage.getCapture(checkBlock.getLocation()).equals(""))
                            return 1;
                    }
                }
            }
        }
        return 2;
    }
    
    public Boolean isAllied(Player Player, String sOtherPlayer) {
        return groupPlugin.isAllied(Player, sOtherPlayer);
    }

    public int getColor(Player player) {
        ChatColor color = groupPlugin.getGroupColor(player);        
        if(color == null || !Colors.containsKey(color.name().toLowerCase()))
            return Colors.get("default");
        else
            return Colors.get(color.name().toLowerCase());
    }
    
    public boolean getDieFromUncapturedPortal() {
        return dieorbounce;
    }
    
    public boolean getWormholeSupport() {
        return enablewormholes;
    }
    
    public boolean getEnderSupport() {
        return enable_ender;
    }
    
    public int getCooldownInterval() {
        return cooldown_message_timeleft_increments;
    }
    
    public int getCoolMessageTime() {
        return cooldown_message_timeleft;
    }
    
    public static Boolean getFullgroupnames() {
        return fullgroupnames;
    }
}