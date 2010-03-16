require 'rho/rhocontroller'

class ContactController < Rho::RhoController

  # GET /Contact/{1}
  def show
    @contact = Contact.find(:first, :conditions => ["offer_id = '#{@params['id'].delete('{}')}'"])
    render :action => :show
  end

  # POST /Contact/create
  def create
    @contact = Contact.find(:first, :conditions => ["offer_id = '#{@params['contact']['offer_id'].delete('{}')}'"])
    puts "Contacto entrando: #{@contact.inspect}"
    if @contact
      puts "Contacto: #{@contact.inspect}"
      WebView.navigate(url_for(:action => :show, :id => @params['contact']['offer_id']))
    else
      puts "No hay contacto todavia"
      @contact = Contact.new(@params['contact'])
      @contact.save
      SyncEngine.dosync
      Contact.set_notification("/app/Contact/sync_notify", "offer_id=#{@params['contact']['offer_id']}")
      render :action => :wait
    end

  end
  
  def sync_notify
     status = @params['status'] ? @params['status'] : ""
     if status == "error"
       errCode = @params['error_code'].to_i
       if errCode == Rho::RhoError::ERR_CUSTOMSYNCSERVER
         @msg = @params['error_message']
       else 
         @msg = Rho::RhoError.new(errCode).message
       end
       WebView.navigate(url_for(:action => :server_error, :query => {:msg => @msg}))
     elsif status == "ok"
       if SyncEngine::logged_in > 0
         WebView.navigate "/app/Contact/show?id=#{@params['offer_id']}"
       else
         # rhosync has logged us out
         WebView.navigate "/app/Settings/login"      
       end
     end  
   end
   
  def wait
    render :action => 'wait', :layout => :full
  end

end
