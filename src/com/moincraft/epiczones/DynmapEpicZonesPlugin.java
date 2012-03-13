/*
 * This file is part of Dynmap-EpicZones <http://www.moincraft.com/>.
 *
 * Dynmap-EpicZones is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dynmap-EpicZones is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.moincraft.epiczones;

import com.randomappdev.EpicZones.EpicZones;
import com.randomappdev.EpicZones.General;
import com.randomappdev.EpicZones.objects.EpicZone;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DynmapEpicZonesPlugin extends JavaPlugin {
    private static final Logger log = Logger.getLogger("Minecraft");
    private static final String LOG_PREFIX = "[dynmap-epiczones] ";
    private static final String DEF_INFOWINDOW = "<div class=\"infowindow\"><span style=\"font-size:120%;\">%regionname%</span><br /> Owner <span style=\"font-weight:bold;\">%playerowners%</span><br />Enter text: <span style=\"font-weight:bold;\">%entertext%</span><br />Exit text: <span style=\"font-weight:bold;\">%exittext%</span><br />Flags<br /><span style=\"font-weight:bold;\">%flags%</span></div>";
    Plugin dynmap;
    DynmapAPI api;
    MarkerAPI markerapi;
    EpicZones epic;
    boolean stop;

    MarkerSet set;
    long updperiod;
    boolean use3d;
    int maxdepth;
    String infowindow;
    AreaStyle defstyle;
    Map<String, AreaStyle> cusstyle;
    Set<String> visible;
    Set<String> hidden;
    
    private static class AreaStyle {
        String strokecolor;
        double strokeopacity;
        int strokeweight;
        String fillcolor;
        double fillopacity;
        int y;

        AreaStyle(FileConfiguration cfg, String path, AreaStyle def) {
            strokecolor = cfg.getString(path+".strokeColor", def.strokecolor);
            strokeopacity = cfg.getDouble(path+".strokeOpacity", def.strokeopacity);
            strokeweight = cfg.getInt(path+".strokeWeight", def.strokeweight);
            fillcolor = cfg.getString(path+".fillColor", def.fillcolor);
            fillopacity = cfg.getDouble(path+".fillOpacity", def.fillopacity);
            y = cfg.getInt(path+".y", def.y);
        }

        AreaStyle(FileConfiguration cfg, String path) {
            strokecolor = cfg.getString(path+".strokeColor", "#FF0000");
            strokeopacity = cfg.getDouble(path+".strokeOpacity", 0.8);
            strokeweight = cfg.getInt(path+".strokeWeight", 3);
            fillcolor = cfg.getString(path+".fillColor", "#FF0000");
            fillopacity = cfg.getDouble(path+".fillOpacity", 0.35);
            y = cfg.getInt(path+".y", 64);
        }
    }
    
    public static void info(String msg) {
        log.log(Level.INFO, LOG_PREFIX + msg);
    }
    public static void severe(String msg) {
        log.log(Level.SEVERE, LOG_PREFIX + msg);
    }

    private class EpicZonesUpdate implements Runnable {
    	public boolean repeat;
        public void run() {
            if(!stop) {
                updateZones();
                if(repeat)
                	getServer().getScheduler().scheduleSyncDelayedTask(DynmapEpicZonesPlugin.this, EpicZonesUpdate.this, updperiod);
            }
        }
    }
    
    private Map<String, AreaMarker> epicMarkers = new HashMap<String, AreaMarker>();
    
    private String formatInfoWindow(EpicZone res) {
        String v = "<div class=\"regioninfo\">" + infowindow + "</div>";
        v = v.replace("%regionname%", res.getName());
        if(res.getOwners().size() == 0) {
        	v = v.replace("%playerowners%", "Nobody");
        } else {
        	v = v.replace("%playerowners%", res.getOwners().toString());
        }
        v = v.replace("%entertext%", res.getEnterText());
        v = v.replace("%exittext%", res.getExitText());
        //Map<String, EpicZonePermission> p = res.getPermissions();
        v = v.replace("%flags%", flags(res));
        return v;
    }
    
    private String flags(EpicZone res) {
    	String flags = "";
    	flags += format("PVP", res.getPVP());
    	flags += format("FIRE IGNITE", res.getFire().getIgnite());
    	flags += format("FIRE SPREAD", res.getFire().getSpread());
    	flags += format("EXPLODE CREEPER", res.getExplode().getCreeper());
    	flags += format("EXPLODE GHAST", res.getExplode().getGhast());
    	flags += format("EXPLODE TNT", res.getExplode().getTNT());
    	flags += format("EXPLODE CREEPER", res.getExplode().getCreeper());
    	flags += format("SANCTUARY", res.getSanctuary());
    	flags += format("FIREBUNSMOBS", res.getFireBurnsMobs());
    	flags += format("ENDERMENPICK", res.getAllowEndermenPick());
    	//flags += format("REGEN", res.getRegen().);
    	//flags += format("MOBS", res.getMobs().());
    	return flags;
    }
    
    private String format(String text, boolean statement) {
    	return text + ": " + onOff(statement);
    }
    
    private String onOff(boolean statement) {
    	if(statement) return "ON<br/>";
    	else return "OFF<br/>";
    }
    
    private boolean isVisible(String id, String worldname) {
        if((visible != null) && (visible.size() > 0)) {
            if((!visible.contains(id)) && (!visible.contains("world:" + worldname))) {
                return false;
            }
        }
        if((hidden != null) && (hidden.size() > 0)) {
            if(hidden.contains(id) || hidden.contains("world:" + worldname))
                return false;
        }
        return true;
    }
        
    private void addStyle(String resid, AreaMarker m) {
        AreaStyle as = cusstyle.get(resid);
        if(as == null)
            as = defstyle;
        int sc = 0xFF0000;
        int fc = 0xFF0000;
        try {
            sc = Integer.parseInt(as.strokecolor.substring(1), 16);
            fc = Integer.parseInt(as.fillcolor.substring(1), 16);
        } catch (NumberFormatException nfx) {
            //Do nothing
        }
        m.setLineStyle(as.strokeweight, as.strokeopacity, sc);
        m.setFillStyle(as.fillopacity, fc);
        m.setRangeY(as.y, as.y);
    }

    private void handleResidence(EpicZone res, Map<String, AreaMarker> newmap) {
        String name = res.getName();
        String wname = res.getWorld();
        String id = wname + "%" + name;
        String desc = formatInfoWindow(res);
        if(isVisible(res.getName(), wname)) {
        
	        World world = this.getServer().getWorld(wname);
	        if(world == null || res.getPolygon() == null) return;
	        int xX = (int) res.getPolygon().getBounds2D().getCenterX();
	        int yY = (int) res.getPolygon().getBounds2D().getCenterY();
	        Block blk = world.getBlockAt(xX, world.getHighestBlockYAt(xX, yY), yY);
	        
	        Location low = blk.getLocation().clone();
	        Location high = blk.getLocation().clone();
	        low.setY(res.getFloor());
	        high.setY(res.getCeiling());
        	
	        ArrayList<Point> points = res.getPointsArray();
            double[] x = new double[points.size()];
            double[] z = new double[points.size()];
	        for(int i = 0; i < points.size(); i++) {
	            Point pt = points.get(i);
	            x[i] = pt.getX(); z[i] = pt.getY();
	        }
	    
	        AreaMarker m = epicMarkers.remove(id);
	        if(m == null) {
	            m = set.createAreaMarker(id, name, false, wname, x, z, false);
	            if(m == null) return;
	        }
	        else {
	            m.setCornerLocations(x, z); /* Replace corner locations */
	            m.setLabel(name);   /* Update label */
	        }
	        if(use3d) { /* If 3D? */
	            m.setRangeY(high.getY()+1.0, low.getY());
	        }
	        m.setDescription(desc); /* Set popup */
	    
	        /* Set line and fill properties */
	        addStyle(id, m);
	
	        newmap.put(id, m);
        }
    }
    
    /* Update residence information */
    private void updateZones() {
        Map<String,AreaMarker> newmap = new HashMap<String,AreaMarker>(); /* Build new map */
        
        if(epic != null) {
            /* Loop through residences */
            for (String key : General.myZones.keySet())
            {
            	EpicZone res = General.myZones.get(key);
                if(res == null) continue;
                /* Handle residence */
                handleResidence(res, newmap);
            }
        }
        /* Now, review old map - anything left is gone */
        for(AreaMarker oldm : epicMarkers.values()) {
            oldm.deleteMarker();
        }
        /* And replace with new map */
        epicMarkers = newmap;        
    }

    private class OurServerListener implements Listener {
        @SuppressWarnings("unused")
        @EventHandler(priority = EventPriority.MONITOR)
        public void onPluginEnable(PluginEnableEvent event) {
            Plugin p = event.getPlugin();
            String name = p.getDescription().getName();
            if(name.equals("dynmap") || name.equals("EpicZones")) {
                if(dynmap.isEnabled() && epic.isEnabled())
                    activate();
            }
        }
    }
    
    public void onEnable() {
        info("initializing");
        PluginManager pm = getServer().getPluginManager();
        /* Get dynmap */
        dynmap = pm.getPlugin("dynmap");
        if(dynmap == null) {
            severe("Cannot find dynmap!");
            return;
        }
        api = (DynmapAPI)dynmap; /* Get API */
        /* Get Residence */
        Plugin p = pm.getPlugin("EpicZones");
        if(p == null) {
            severe("Cannot find EpicZones!");
            return;
        }
        epic = (EpicZones)p;
        getServer().getPluginManager().registerEvents(new OurServerListener(), this);
        /* If both enabled, activate */
        if(dynmap.isEnabled() && epic.isEnabled())
            activate();
    }

    private void activate() {
        /* Now, get markers API */
        markerapi = api.getMarkerAPI();
        if(markerapi == null) {
            severe("Error loading dynmap marker API!");
            return;
        }

        /* Load configuration */
        FileConfiguration cfg = getConfig();
        cfg.options().copyDefaults(true);   /* Load defaults, if needed */
        this.saveConfig();  /* Save updates, if needed */
        
        /* Now, add marker set for mobs (make it transient) */
        set = markerapi.getMarkerSet("epiczones.markerset");
        if(set == null)
            set = markerapi.createMarkerSet("epiczones.markerset", cfg.getString("layer.name", "EpicZones"), null, false);
        else
            set.setMarkerSetLabel(cfg.getString("layer.name", "EpicZones"));
        if(set == null) {
            severe("Error creating marker set");
            return;
        }
        set.setLayerPriority(cfg.getInt("layer.layerprio", 10));
        set.setHideByDefault(cfg.getBoolean("layer.hidebydefault", false));
        int minzoom = cfg.getInt("layer.minzoom", 0);
        if(minzoom > 0)
            set.setMinZoom(minzoom);
        use3d = cfg.getBoolean("use3dregions", false);
        maxdepth = cfg.getInt("resdepth", 2);
        if(maxdepth < 1) maxdepth = 1;
        infowindow = cfg.getString("infowindow", DEF_INFOWINDOW);
        
        /* Get style information */
        defstyle = new AreaStyle(cfg, "regionstyle");
        cusstyle = new HashMap<String, AreaStyle>();
        ConfigurationSection sect = cfg.getConfigurationSection("custstyle");
        if(sect != null) {
            Set<String> ids = sect.getKeys(false);
            
            for(String id : ids) {
                cusstyle.put(id, new AreaStyle(cfg, "custstyle." + id, defstyle));
            }
        }
        List vis = cfg.getList("visibleregions");
        if(vis != null) {
            visible = new HashSet<String>(vis);
        }
        List hid = cfg.getList("hiddenregions");
        if(hid != null) {
            hidden = new HashSet<String>(hid);
        }

        /* Set up update job - based on periond */
        int per = cfg.getInt("update.period", 300);
        if(per < 15) per = 15;
        updperiod = (long)(per*20);
        stop = false;
        
        EpicZonesUpdate updater = new EpicZonesUpdate();
        updater.repeat = true;
        getServer().getScheduler().scheduleSyncDelayedTask(this, updater, 40);   /* First time is 2 seconds */
        
        info("version " + this.getDescription().getVersion() + " is activated");
    }

    public void onDisable() {
        if(set != null) {
            set.deleteMarkerSet();
            set = null;
        }
        epicMarkers.clear();
        stop = true;
    }

}
