package cz.trinera.anakon.dtd_executor.dtd_definitions.sample.coordinates;

public class ItemProcessorTest {

   /* @Test
    public void testMissingDf034() {
        MarcCoordinatesConstencyCheckProcess.AnakonCoordsSearchResult.AnakonItems.Item.ItemData data = new MarcCoordinatesConstencyCheckProcess.AnakonCoordsSearchResult.AnakonItems.Item.ItemData();
        MarcCoordinatesConstencyCheckProcess.ItemProcessor processor = new MarcCoordinatesConstencyCheckProcess.ItemProcessor();
        boolean result = processor.process(data);
        //should be invalid due to missing fields (df_034)
        assertFalse(result);
        assertEquals(processor.getErrorMessage(), "Missing field df_034");
    }

    @Test
    public void testMzk01Nkc20172897258() {
        //nkc20172897258
        MarcCoordinatesConstencyCheckProcess.AnakonCoordsSearchResult.AnakonItems.Item.ItemData data = new MarcCoordinatesConstencyCheckProcess.AnakonCoordsSearchResult.AnakonItems.Item.ItemData();
        MarcCoordinatesConstencyCheckProcess.ItemProcessor processor = new MarcCoordinatesConstencyCheckProcess.ItemProcessor();
        data.df_034 = new ArrayList<>();
        var coords = new MarcCoordinatesConstencyCheckProcess.AnakonCoordsSearchResult.AnakonItems.Item.ItemData.Parted_coords();
        coords.d = "E0141328";
        coords.e = "E0144224";
        coords.f = "N0501038";
        coords.g = "N0495630";
        data.df_034.add(coords);

        data.df_255 = new ArrayList<>();
        var c = new MarcCoordinatesConstencyCheckProcess.AnakonCoordsSearchResult.AnakonItems.Item.ItemData.Coords();
        c.c = "(E 14°13'28\"--E 14°42'24\"/N 50°10'38\"--N 49°56'30\")";
        data.df_255.add(c);
        boolean result = processor.process(data);
        //should be invalid due to missing fields (df_034)
        assertFalse(result);
        assertEquals(processor.getErrorMessage(), "Index 0: [Warning]: Extra characters \"(\" around the expression; [Warning]: Extra characters \")\" around the expression| ");
    }
    */
}

//poznamky: musi se i hledat to, kde 034 je a 255 neni, ale i naopak (255 je a 034 neni)
//pozor, u jine baze (autoritni mzk04), kde tam je jiny postup
//nevidime v proces logu postup, (celkem x zaznamu. prochazim 100/1000)
//chainovani: mame warning a error na radku
//uzavorkovani je v pohode, nekonzistence je spatne (jedna zavorka, kombinace)

//2 vystupy: errory a warningy. Warningy aktualne zahazovat

//dneska:
//- zavorky
//- integrovat jako proces
//- warningy nevypisovat
//- filtr na bazi

//pozdeji
//- warning a error budou jine vystupy
//- jeden radek budou chainovane vsechny errory

//dalsi milestone - pred AKM. 26/27.11. Sualeph 24./25.11.
//milestone
//cache na MODS atd.







