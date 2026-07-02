package com.ntt;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.swing.border.CompoundBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * @author  M. Ziggiotti - NTT Data Spa - Rome, IT
 * @version 202607.021310
 */
public class FlatFileGenerator  extends JFrame {
    
    private static boolean DEBUG = false;
    private static boolean TEMPLATE_MODIFIED = false;
    
    private static final String VERSION = "Ver. 1.0 - 202607.021310";
    private transient List<FileRecord> fileRecords = null;
    private final transient DateTimeFormatter df = DateTimeFormatter.ofPattern("yyMMdd");

    // table header
    private final transient String[] intestazioni = {"Field name", "Offset", "Length", "Value (Input)"};
    
    private final transient JTable tabellaCampi;
    private final transient DefaultTableModel modelloTabella;
    private final transient Font cellFont = new Font("Tahoma", Font.PLAIN, 12);
    private final transient Font headerFont = cellFont.deriveFont(Font.BOLD);

    private transient JCheckBox cbAppend;
    private transient JTextField tfFileName;
    private transient JFileChooser fileChooser; 
  

    /**
     * Constructor
     */
    public FlatFileGenerator() {
        loadFlatStructure();

        modelloTabella = createTableModel();
        tabellaCampi = new JTable(modelloTabella);
        initGui();
    }

    private void initGui() {
        debug("Initialing UI...");

        setTitle("CNOR Generator - " + VERSION + " - NTTDATA, Rome");
        setSize(800, 650);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        fileChooser = new JFileChooser(new File(".").getAbsolutePath());
        fileChooser.setDialogTitle("Seleziona dove salvare il file");

        initTable();
                
        JScrollPane scrollPane = new JScrollPane(tabellaCampi);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        cbAppend = new JCheckBox(" Append mode ");
        cbAppend.setHorizontalTextPosition(SwingConstants.LEFT);
        cbAppend.setFont(cellFont);

        tfFileName = new JTextField( createDefaultFileName() );
        tfFileName.setPreferredSize(new Dimension(270, 35));
        tfFileName.setFont(new Font("Courier new", Font.PLAIN, 12));

        JPanel commandPanel = createCommandPanel();

        add(scrollPane, BorderLayout.CENTER);
        add(commandPanel, BorderLayout.SOUTH);

    }
    

    /**
     * Creates command panels, putting it at the bottom
     * @return 
     */
    private JPanel createCommandPanel() {
        JButton btnGenera = new JButton("Generate");
            btnGenera.setPreferredSize(new Dimension(160, 35));
            btnGenera.setBackground(new Color(76, 175, 80));
            btnGenera.setForeground(Color.WHITE);
            btnGenera.setFont(headerFont);
            btnGenera.setFocusPainted(false);
            btnGenera.addActionListener(e -> generateFile());
        
        JButton reloadTemplate = new JButton();
            reloadTemplate.setToolTipText("Reloads template ad its defaults");
            reloadTemplate.setIcon(new ImageIcon(IconLibrary.SEARCH_ICON));
            reloadTemplate.addActionListener(e -> reloadTemplate() );

        // Command panel
        JPanel commandPanel = new JPanel();
            commandPanel.setBorder(new CompoundBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), BorderFactory.createEtchedBorder()) );
            commandPanel.add(reloadTemplate);
            commandPanel.add(cbAppend);
            commandPanel.add( Box.createHorizontalStrut(20) );
            commandPanel.add(new JLabel("Output file: "));
            commandPanel.add(tfFileName);
            commandPanel.add( Box.createHorizontalStrut(20) );
            commandPanel.add(btnGenera);
            
        return commandPanel;
    }


    /**
     * Shows a file dialog and generates the file if user press 'Save' button
     */
    private void generateFile() {
        fileChooser.setSelectedFile(new File(tfFileName.getText()));
        int userSelection = fileChooser.showSaveDialog(this);
        
        if (userSelection == JFileChooser.CANCEL_OPTION) {
            return;
        }

        debug("Generating flat file...");

        tfFileName.setText( fileChooser.getSelectedFile().getName() );
        if (tabellaCampi.isEditing()) {
            tabellaCampi.getCellEditor().stopCellEditing();
        }

        StringBuilder rigaCompleta = new StringBuilder(8*1024);

        // 3. Elabora ogni riga leggendo dinamicamente la lunghezza specifica dal modello
        for (int i = 0, max= fileRecords.size(); i < max; i++) {
            int lunghezzaAttuale = (int) modelloTabella.getValueAt(i, 2);
            String valoreRaw = (String) modelloTabella.getValueAt(i, 3);
            
            if (valoreRaw == null) {
                valoreRaw = "";
            }

            // Applica la formattazione con la lunghezza specifica di questo campo
            String valoreFormattato = formattaLunghezzaFissa(valoreRaw, lunghezzaAttuale);
            rigaCompleta.append(valoreFormattato);
        }
        
        File fileChoosen = fileChooser.getSelectedFile();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileChoosen, cbAppend.isSelected()))) {
            writer.write(rigaCompleta.toString());
            writer.newLine();

            JOptionPane.showMessageDialog(this,
                                          "Flat file successfully generated\nTotal row characters: " + rigaCompleta.length(),
                                          "Success", 
                                          JOptionPane.INFORMATION_MESSAGE);
    
            debug("Flat file successfully generated\nTotal row characters: " + rigaCompleta.length());
        }
        catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                                          "Writing error: " + ex.getMessage(),
                                          "Error", 
                                          JOptionPane.ERROR_MESSAGE);
        }
    }

    private String formattaLunghezzaFissa(String testo, int lunghezza) {
        if (testo.length() >= lunghezza) {
            return testo.substring(0, lunghezza);
        }
        return String.format("%-" + lunghezza + "s", testo);
    }


    /**
     * Loads the 'tracciato.txt' flat structure file
     */
    private void loadFlatStructure()  {
        debug("Loading record track file...");

        try (InputStream is = getClass().getResourceAsStream("/tracciato.txt")) {
            
            if (is == null) {
                throw new IllegalArgumentException("File [tracciato.txt] non presente.");
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8) ) ) {
               AtomicInteger contatore = new AtomicInteger(1);
                fileRecords =  reader.lines()
                                .filter(line -> !line.startsWith("#"))
                                .map(row -> {
                                        debug("Reading line #: " + contatore.getAndIncrement()); 
                                        return row.split(";", -1);
                                    }
                                )
                                .map(values -> new FileRecord(values[0], values[1], values[2], values[3]))
                                .collect(Collectors.toList() );
            }
        }
        catch(IOException ioEx) {
            System.err.println("Cannot load file structure definitions: " + ioEx.getMessage());
        }
    }

    
    private void reloadTemplate() {
        if(TEMPLATE_MODIFIED) {
            int choose = JOptionPane.showConfirmDialog(this, 
                                                       "Some values were updated. Reload template and lost changes ?", 
                                                       "Template modified", 
                                                       JOptionPane.WARNING_MESSAGE);
            if(choose == JOptionPane.CANCEL_OPTION) {
                return;
            }
        }

        TEMPLATE_MODIFIED = false;
        loadFlatStructure();
        tabellaCampi.setModel( createTableModel() );
        initTable();
    }

    
    private DefaultTableCellRenderer createTableCellRenderer() {
        Font fontSpeciale = new Font("Tahoma", Font.BOLD, 12);
        return  new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setFont(fontSpeciale);
                
                return c;
            }
        };
    }
    

    private String createDefaultFileName() {
        String nomeFile = "SCTFUNCRITMM%s001AI1";
        String today = df.format( LocalDate.now() );
        
        return String.format(nomeFile, today);
    }


    public static void main(String[] args) {
        DEBUG = (args.length > 0 && args[0].contains("-debug") );
        debug("Flat File Generator [" + VERSION + "] starting");
        SwingUtilities.invokeLater(() -> new FlatFileGenerator().setVisible(true));
    }

    
    private DefaultTableModel createTableModel() {
            DefaultTableModel tableModel =  new DefaultTableModel(intestazioni, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 3; // Solo la colonna "Valore" e' modificabile
            }
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                // Permette l'allineamento corretto e l'ordinamento numerico per le colonne offset e length
                if (columnIndex == 1 || columnIndex == 2) {
                    return Integer.class;
                }
                return String.class;
            }
        };

        for (int i = 0, max= fileRecords.size(); i < max; i++) {
                FileRecord fRec = fileRecords.get(i);
                tableModel.addRow(new Object[]{ fRec.getName(), 
                                                    fRec.getOffset(), 
                                                    fRec.getLength(), 
                                                    fRec.getDefaultValue()
                                     });
        }    
        return tableModel;
    }


    public static void debug(String msg) {
        if(DEBUG) {
            System.out.println(msg); 
        }
    }

    
    /**
     * Initializes some table behavior
     */
    private void initTable() {
                tabellaCampi.setRowHeight(22);
        tabellaCampi.setFont(cellFont);
        
        // Blocca lo spostamento delle colonne per sicurezza
        tabellaCampi.getTableHeader().setReorderingAllowed(false);

        tabellaCampi.getTableHeader().setFont(headerFont);
        tabellaCampi.getColumnModel().getColumn(0).setMinWidth(150);
        tabellaCampi.getColumnModel().getColumn(0).setMaxWidth(150);
        tabellaCampi.getColumnModel().getColumn(1).setMinWidth(120);
        tabellaCampi.getColumnModel().getColumn(1).setMaxWidth(120);
        tabellaCampi.getColumnModel().getColumn(2).setMinWidth(120);
        tabellaCampi.getColumnModel().getColumn(2).setMaxWidth(120);
        tabellaCampi.getColumnModel().getColumn(3).setCellRenderer( createTableCellRenderer() );
        tabellaCampi.getModel().addTableModelListener((TableModelEvent e) -> {
            // Intercepts just cell's UPDATE event
            if (e.getType() == TableModelEvent.UPDATE) {
                debug( String.format("Cell updated at [row, column]: %d, %d", e.getFirstRow(), e.getColumn() ));
                TEMPLATE_MODIFIED = true;
                System.out.println("Cell updated");
            }
        });

    }


        
    class FileRecord {
        String name;
        int length;
        int offset;
        String defaultValue;
        
        public FileRecord(String aName, String anOffset, String aLen, String aDefault) {
            this.name = aName;
            this.offset = Integer.parseInt(anOffset);
            this.length = Integer.parseInt(aLen);
            this.defaultValue = aDefault;
        }

        public String getName() {
            return name;
        }

        public int getLength() {
            return length;
        }

        public int getOffset() {
            return offset;
        }
        
        public String getDefaultValue() {
            return defaultValue;
        }

    }
}
